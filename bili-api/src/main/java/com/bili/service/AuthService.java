package com.bili.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.dto.AuthDtos;
import com.bili.entity.CoinFlow;
import com.bili.entity.FavoriteFolder;
import com.bili.entity.User;
import com.bili.mapper.CoinFlowMapper;
import com.bili.mapper.UserMapper;
import com.bili.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证服务（FR-AU01/02/05，ADR-01/12/13/14；Phase 3 增 FR-IN11 注册赠币与每日签到）。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] NICK_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    /** 注册赠送初始硬币（Phase 3 裁决：50 枚） */
    private static final int REGISTER_BONUS = 50;
    /** 每日签到发放枚数（Phase 3 裁决：5 枚/天，固定不递增） */
    private static final int CHECK_IN_REWARD = 5;

    private final UserMapper userMapper;
    private final CoinFlowMapper coinFlowMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 忘记密码内存存储（ADR-14）：email → 验证码记录 */
    private static final Map<String, CodeRecord> CODE_STORE = new ConcurrentHashMap<>();
    /** email → 一次性重置凭证 */
    private static final Map<String, TokenRecord> TOKEN_STORE = new ConcurrentHashMap<>();

    private record CodeRecord(String code, long expireAt, long lastSentAt) {}
    private record TokenRecord(String token, long expireAt) {}

    /** 验证码/凭证有效期与冷却（ADR-14 / FR-AU05） */
    private static final long CODE_TTL_MILLIS = 10 * 60 * 1000L;
    private static final long CODE_COOLDOWN_MILLIS = 60 * 1000L;

    public AuthService(UserMapper userMapper, CoinFlowMapper coinFlowMapper, JwtUtil jwtUtil,
                       com.bili.mapper.FavoriteFolderMapper favoriteFolderMapper) {
        this.userMapper = userMapper;
        this.coinFlowMapper = coinFlowMapper;
        this.jwtUtil = jwtUtil;
        this.favoriteFolderMapper = favoriteFolderMapper;
    }

    private final com.bili.mapper.FavoriteFolderMapper favoriteFolderMapper;

    /**
     * 注册（FR-AU01）：小写化→查重→BCrypt→初始昵称→注册赠币（FR-IN11）→即登录。
     * Phase 3 起多表写（user + coin_flow）加 @Transactional（ADR-18）。
     */
    @Transactional
    public AuthDtos.LoginResp register(String rawEmail, String password) {
        String email = rawEmail.trim().toLowerCase();
        Long existed = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
        if (existed != null && existed > 0) {
            throw new BusinessException(ErrorCode.EMAIL_REGISTERED);
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(password));
        user.setNickname(generateNickname());
        user.setCoin(REGISTER_BONUS); // 注册赠送 50（显式写入，非列默认 0）
        // avatar_url/gender/sign 走数据库默认值；显式置 null 由 MP 跳过该列
        userMapper.insert(user);
        // 注册赠送流水（biz_type=2，FR-IN11；流水即账本）
        CoinFlow bonus = new CoinFlow();
        bonus.setUserId(user.getId());
        bonus.setChangeAmount(REGISTER_BONUS);
        bonus.setBizType(2);
        coinFlowMapper.insert(bonus);
        // 默认收藏夹（Phase 4 三写，FR-IN12 / ADR-20：新用户进播放页收藏弹层永远有夹可选）
        FavoriteFolder defaultFolder = new FavoriteFolder();
        defaultFolder.setUserId(user.getId());
        defaultFolder.setName(FavoriteFolder.DEFAULT_NAME);
        defaultFolder.setIsDefault(1);
        favoriteFolderMapper.insert(defaultFolder);
        // 插库后重查：让默认值（avatar_url 等）回填到响应对象（FR-PF04：新用户必须有默认头像）
        User saved = userMapper.selectById(user.getId());
        return buildLoginResp(saved != null ? saved : user);
    }

    /**
     * API-AU07 每日签到（FR-IN11：固定 5 枚）。
     * 防重以 coin_flow 当日（东八区自然日）biz_type=1 是否存在为准（流水即签到记录，不加独立签到表）；
     * 两步（+5 + 插流水）同一事务（ADR-18）。
     */
    @Transactional
    public int checkIn(long uid) {
        java.time.LocalDateTime todayStart = LocalDate.now().atStartOfDay(); // 服务器时区（连接串已带 Asia/Shanghai）
        if (coinFlowMapper.countTodayCheckIn(uid, todayStart) > 0) {
            throw new BusinessException(ErrorCode.ALREADY_CHECKED_IN); // 40906
        }
        userMapper.increaseCoin(uid, CHECK_IN_REWARD);
        CoinFlow flow = new CoinFlow();
        flow.setUserId(uid);
        flow.setChangeAmount(CHECK_IN_REWARD);
        flow.setBizType(1); // 签到
        coinFlowMapper.insert(flow);
        User after = userMapper.selectById(uid);
        return after.getCoin(); // 签到后余额
    }

    /** 登录（FR-AU02）：统一错误文案防账号枚举 */
    public AuthDtos.LoginResp login(String rawEmail, String password) {
        String email = rawEmail.trim().toLowerCase();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null || !encoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "邮箱或密码错误");
        }
        return buildLoginResp(user);
    }

    /** 忘记密码第一步（FR-AU05）：发码（Mock 控制台） */
    public void sendResetCode(String rawEmail) {
        String email = rawEmail.trim().toLowerCase();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_REGISTERED); // 40402（TD-13：不防枚举）
        }
        long now = System.currentTimeMillis();
        CodeRecord prev = CODE_STORE.get(email);
        if (prev != null && now - prev.lastSentAt() < CODE_COOLDOWN_MILLIS) {
            throw new BusinessException(ErrorCode.SEND_TOO_FREQUENT); // 42901
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        CODE_STORE.put(email, new CodeRecord(code, now + CODE_TTL_MILLIS, now));
        // Mock 发送（ADR-14）：这就是「发送」动作本身，敏感信息纪律的刻意例外（03 §5.6）
        log.info("[MockMail] to={} code={}", email, code);
    }

    /** 忘记密码第二步（FR-AU05）：校验验证码 → 签发一次性 resetToken */
    public AuthDtos.ResetTokenResp verifyResetCode(String rawEmail, String code) {
        String email = rawEmail.trim().toLowerCase();
        CodeRecord rec = CODE_STORE.get(email);
        if (rec == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请先获取验证码");
        }
        long now = System.currentTimeMillis();
        if (now > rec.expireAt()) {
            CODE_STORE.remove(email);
            throw new BusinessException(ErrorCode.PARAM_INVALID, "验证码已过期，请重新获取");
        }
        if (!rec.code().equals(code)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "验证码错误");
        }
        CODE_STORE.remove(email); // 验证通过即作废（API-AU05 备注）
        String token = randomToken();
        TOKEN_STORE.put(email, new TokenRecord(token, now + CODE_TTL_MILLIS));
        return new AuthDtos.ResetTokenResp(token, CODE_TTL_MILLIS / 1000);
    }

    /** 忘记密码第三步（FR-AU05）：凭 resetToken 重置密码，不自动登录 */
    public void resetPassword(String resetToken, String newPassword) {
        Map.Entry<String, TokenRecord> entry = TOKEN_STORE.entrySet().stream()
                .filter(e -> e.getValue().token().equals(resetToken))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_INVALID, "重置链接已失效，请重新操作"));
        TokenRecord rec = entry.getValue();
        TOKEN_STORE.remove(entry.getKey()); // 一次性：无论成功失败均作废
        if (System.currentTimeMillis() > rec.expireAt()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "重置链接已失效，请重新操作");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, entry.getKey()));
        if (user == null) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_REGISTERED);
        }
        user.setPasswordHash(encoder.encode(newPassword));
        userMapper.updateById(user);
    }

    private AuthDtos.LoginResp buildLoginResp(User user) {
        JwtUtil.TokenResult tr = jwtUtil.generate(user.getId());
        AuthDtos.LoginResp resp = new AuthDtos.LoginResp();
        resp.token = tr.token();
        resp.expiresIn = tr.expiresInSeconds();
        resp.user = new AuthDtos.LoginResp.UserSummary(
                user.getId(), user.getNickname(), user.getAvatarUrl());
        return resp;
    }

    /** 初始昵称（FR-AU01 备注）：「用户」+4 位随机串，不依赖 ID */
    private String generateNickname() {
        StringBuilder sb = new StringBuilder("用户");
        for (int i = 0; i < 4; i++) {
            sb.append(NICK_CHARS[RANDOM.nextInt(NICK_CHARS.length)]);
        }
        return sb.toString();
    }

    private String randomToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : buf) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
