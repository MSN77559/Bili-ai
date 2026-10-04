package com.bili.controller;

import com.bili.common.context.UserContext;
import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.common.result.Result;
import com.bili.dto.VideoDtos;
import com.bili.entity.Video;
import com.bili.mapper.VideoMapper;
import com.bili.service.VideoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 视频接口（05：API-VD01~VD04，Phase 2）。
 * GET /api/videos 与 GET /api/videos/{videoId}、GET /api/users/{id}/videos 为白名单公开接口
 * （可选 token 解析视角，AuthInterceptor 放行）；POST /api/videos 需登录。
 */
@RestController
@RequestMapping("/api/videos")
public class VideoController {

    /** 视频类型白名单（ADR-17）：扩展名小写比对 */
    private static final Set<String> VIDEO_EXTS = Set.of("mp4", "webm");
    /** 封面类型白名单（与头像同一图片白名单，ADR-04/ADR-17） */
    private static final Set<String> COVER_EXTS = Set.of("jpg", "jpeg", "png", "webp");
    /** 视频 Content-Type 白名单（05 API-VD01：扩展名 + Content-Type 双校验） */
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/webm");
    /** 封面 Content-Type 白名单（.jpg/.jpeg 同为 image/jpeg） */
    private static final Set<String> COVER_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    /** 视频大小上限（全局 multipart 同额 500MB；显式校验双保险，ADR-17） */
    private static final long VIDEO_MAX_BYTES = 500L * 1024 * 1024;
    /** 封面大小上限（ADR-17 分层：图片 2MB） */
    private static final long IMAGE_MAX_BYTES = 2L * 1024 * 1024;

    private final VideoService videoService;
    private final VideoMapper videoMapper;

    @Value("${bili.upload.dir}")
    private String uploadDir;

    public VideoController(VideoService videoService, VideoMapper videoMapper) {
        this.videoService = videoService;
        this.videoMapper = videoMapper;
    }

    /** API-VD01 视频投稿（multipart，需登录）。表单字段 title/category/description/duration + file 必选 cover 可选 */
    @PostMapping
    public Result<VideoDtos.PublishResp> publish(@RequestParam("file") MultipartFile file,
                                                 @RequestParam(value = "cover", required = false) MultipartFile cover,
                                                 @RequestParam("title") String title,
                                                 @RequestParam("category") Integer category,
                                                 @RequestParam(value = "description", defaultValue = "") String description,
                                                 @RequestParam("duration") Integer duration) {
        long uid = requireUid();

        // —— 字段校验（FR-VD01）——
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请选择视频文件");
        }
        String t = title == null ? "" : title.trim();
        int tLen = t.codePointCount(0, t.length());
        if (tLen < 2 || tLen > 80) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "标题长度须为 2-80 字符");
        }
        if (category == null || category < 1 || category > 14) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "分区参数不合法");
        }
        String desc = description == null ? "" : description;
        if (desc.codePointCount(0, desc.length()) > 500) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "简介最长 500 字符");
        }
        if (duration == null || duration < 1 || duration > 86400) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "视频时长不合法");
        }
        String videoExt = extOf(file.getOriginalFilename());
        if (!VIDEO_EXTS.contains(videoExt) || !VIDEO_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED, "仅支持 mp4/webm 格式"); // 41501
        }
        if (file.getSize() > VIDEO_MAX_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE); // 41301（全局 multipart 同额兜底）
        }
        String coverUrl = null;
        if (cover != null && !cover.isEmpty()) {
            String coverExt = extOf(cover.getOriginalFilename());
            if (!COVER_EXTS.contains(coverExt) || !COVER_TYPES.contains(cover.getContentType())) {
                throw new BusinessException(ErrorCode.FILE_TYPE_UNSUPPORTED, "封面仅支持 jpg/png/webp 格式");
            }
            if (cover.getSize() > IMAGE_MAX_BYTES) {
                throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
            }
            coverUrl = saveFile(cover, coverExt, "covers");
        }

        // —— 存储 + 插库（ADR-17）——
        String videoUrl = saveFile(file, videoExt, "videos");
        Video video = new Video();
        video.setUserId(uid); // token uid，不信客户端
        video.setTitle(t);
        video.setCategory(category);
        video.setDescription(desc);
        video.setVideoUrl(videoUrl);
        video.setCoverUrl(coverUrl); // 可 null
        video.setDuration(duration);
        // view_count/like_count/deleted 取列默认（insert 不设值，MP 忽略 null 字段）
        videoMapper.insert(video);
        return Result.ok(new VideoDtos.PublishResp(video.getId(), videoUrl, coverUrl));
    }

    /** API-VD02 视频列表（公开分页 + 可选 category；白名单接口，无视角字段） */
    @GetMapping
    public Result<VideoDtos.VideoPageResp> list(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) Integer category) {
        validatePage(page, size);
        if (category != null && (category < 1 || category > 14)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "分区参数不合法");
        }
        return Result.ok(videoService.listVideos(category, page, size));
    }

    /** API-VD03 视频详情（公开可选 token 视角；顺带 view_count+1，ADR-17） */
    @GetMapping("/{videoId}")
    public Result<VideoDtos.VideoDetail> detail(@PathVariable Long videoId) {
        if (videoId == null || videoId <= 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "视频 ID 不合法");
        }
        // 拦截器对白名单接口尽力解析 token；游客 UserContext 为空
        return Result.ok(videoService.getVideoDetail(videoId, UserContext.getUserId()));
    }

    /** API-VD04 空间投稿列表挂在 UserController（/api/users/{userId}/videos，与 followings 同段） */

    /** 分页参数校验（05 §1） */
    private void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 50) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "分页参数不合法");
        }
    }

    private long requireUid() {
        Long uid = UserContext.getUserId();
        if (uid == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED); // 理论不可达（拦截器保证）
        }
        return uid;
    }

    /** UUID 重命名落盘（ADR-04/ADR-17），返回相对路径 /api/files/{subDir}/{filename} */
    private String saveFile(MultipartFile file, String ext, String subDir) {
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Paths.get(uploadDir, subDir);
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename).toAbsolutePath());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        }
        return "/api/files/" + subDir + "/" + filename;
    }

    private String extOf(String filename) {
        if (filename == null) return "";
        int i = filename.lastIndexOf('.');
        return i < 0 ? "" : filename.substring(i + 1).toLowerCase(Locale.ROOT);
    }
}
