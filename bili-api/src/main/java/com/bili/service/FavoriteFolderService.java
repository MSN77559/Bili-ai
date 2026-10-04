package com.bili.service;

import com.bili.common.exception.BusinessException;
import com.bili.common.result.ErrorCode;
import com.bili.dto.InteractDtos;
import com.bili.entity.FavoriteFolder;
import com.bili.mapper.FavoriteFolderMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收藏夹服务（FR-IN12~IN14，Phase 4 / ADR-20）。
 * 默认夹兜底：不可删不可改名（40908）；删普通夹条目同事务移入默认夹（collect_count 不变）；
 * 同名 40909：uk(user_id, name) 兜底 + 预查（DuplicateKeyException 全局处理器默认映射 40901，不适用——此处捕获转 40909）。
 */
@Service
public class FavoriteFolderService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 夹名长度上限（码点计，TB-12 name VARCHAR(20)） */
    private static final int NAME_MAX = 20;

    private final FavoriteFolderMapper folderMapper;

    public FavoriteFolderService(FavoriteFolderMapper folderMapper) {
        this.folderMapper = folderMapper;
    }

    /** API-IN07 夹列表（全量非分页；count 批量 GROUP BY 一次查齐；默认夹恒首位——注册即建最早） */
    public InteractDtos.FolderListResp listFolders(long uid) {
        List<InteractDtos.FolderItem> folders = folderMapper.selectFolders(uid);
        if (!folders.isEmpty()) {
            Map<Long, Long> counts = new HashMap<>();
            for (InteractDtos.FolderCountRow row : folderMapper.countByFolder(uid)) {
                counts.put(row.folderId, row.cnt);
            }
            for (InteractDtos.FolderItem f : folders) {
                f.count = counts.getOrDefault(f.folderId, 0L);
                f.isDefault = f.rawDefault();
                f.createdAt = f.createdAtRaw == null ? null : DT.format(f.createdAtRaw);
                f.createdAtRaw = null;
            }
        }
        return new InteractDtos.FolderListResp(folders);
    }

    /** API-IN08 创建收藏夹（同名 40909；名称 1~20 码点 40001） */
    public InteractDtos.FolderCreateResp create(long uid, String rawName) {
        String name = requireName(rawName);
        FavoriteFolder folder = new FavoriteFolder();
        folder.setUserId(uid);
        folder.setName(name);
        folder.setIsDefault(0);
        try {
            folderMapper.insert(folder); // uk_user_name 兜底防并发撞名
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.FOLDER_NAME_DUPLICATED); // 40909（不走全局 40901 映射）
        }
        return new InteractDtos.FolderCreateResp(folder.getId(), name, 0, false,
                folder.getCreatedAt() == null ? DT.format(java.time.LocalDateTime.now()) : DT.format(folder.getCreatedAt()));
    }

    /** API-IN09 重命名（默认夹 40908；非本人夹/不存在 40405；同名 40909） */
    public void rename(long uid, long folderId, String rawName) {
        FavoriteFolder folder = requireOwnedFolder(uid, folderId);
        if (folder.getIsDefault() != null && folder.getIsDefault() == 1) {
            throw new BusinessException(ErrorCode.DEFAULT_FOLDER_IMMUTABLE); // 40908
        }
        String name = requireName(rawName);
        folder.setName(name);
        try {
            folderMapper.updateById(folder); // uk_user_name 兜底
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.FOLDER_NAME_DUPLICATED);
        }
    }

    /** API-IN10 删除（默认夹 40908；非本人夹/不存在 40405；夹内条目同事务移入默认夹，collect_count 不变） */
    @Transactional
    public void delete(long uid, long folderId) {
        FavoriteFolder folder = requireOwnedFolder(uid, folderId);
        if (folder.getIsDefault() != null && folder.getIsDefault() == 1) {
            throw new BusinessException(ErrorCode.DEFAULT_FOLDER_IMMUTABLE); // 40908
        }
        FavoriteFolder def = folderMapper.selectDefaultFolder(uid); // 注册三写保证恒存在
        if (def != null && def.getId() != folderId) {
            folderMapper.moveFavoritesTo(uid, folderId, def.getId()); // ①条目移入默认夹
        }
        folderMapper.deleteById(folderId); // ②删夹行
    }

    /** folderId 存在且归属本人（不存在与非本人夹统一 40405，不暴露他人夹存在性） */
    public FavoriteFolder requireOwnedFolder(long uid, long folderId) {
        FavoriteFolder folder = folderMapper.selectById(folderId);
        if (folder == null || folder.getUserId() != uid) {
            throw new BusinessException(ErrorCode.FOLDER_NOT_FOUND);
        }
        return folder;
    }

    /** 本人的默认夹 id（IN04 不带 folderId 的收藏目标；注册三写保证存在，防御式 null 检查） */
    public long requireDefaultFolderId(long uid) {
        FavoriteFolder def = folderMapper.selectDefaultFolder(uid);
        if (def == null) {
            // 理论不可达（注册三写 + 存量回填）；兜底现场创建
            def = new FavoriteFolder();
            def.setUserId(uid);
            def.setName(FavoriteFolder.DEFAULT_NAME);
            def.setIsDefault(1);
            folderMapper.insert(def);
        }
        return def.getId();
    }

    /** 名称校验：trim 后 1~20 码点 */
    private String requireName(String rawName) {
        if (rawName == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "收藏夹名称须为 1-20 字符");
        }
        String name = rawName.trim();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > NAME_MAX) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "收藏夹名称须为 1-20 字符");
        }
        return name;
    }
}
