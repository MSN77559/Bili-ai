package com.bili.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bili.dto.InteractDtos;
import com.bili.entity.FavoriteFolder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 收藏夹 Mapper（TB-12，Phase 4 / FR-IN12~IN16，ADR-20）。
 */
@Mapper
public interface FavoriteFolderMapper extends BaseMapper<FavoriteFolder> {

    /** API-IN07 夹列表（created_at ASC 默认夹恒首位；count 批量 GROUP BY 一次查齐防 N+1） */
    @Select("SELECT f.id AS folderId, f.name, f.is_default AS isDefaultRaw, f.created_at AS createdAtRaw " +
            "FROM favorite_folder f WHERE f.user_id = #{userId} ORDER BY f.created_at ASC, f.id ASC")
    List<InteractDtos.FolderItem> selectFolders(@Param("userId") long userId);

    /** 批量取各夹有效收藏数（IN07 count 字段；GROUP BY 一次查齐） */
    @Select("SELECT folder_id AS folderId, COUNT(*) AS cnt FROM favorite " +
            "WHERE user_id = #{userId} AND deleted = 0 GROUP BY folder_id")
    List<InteractDtos.FolderCountRow> countByFolder(@Param("userId") long userId);

    /** 本人的默认夹（删普通夹时条目移入目标；注册三写保证恒存在） */
    @Select("SELECT * FROM favorite_folder WHERE user_id = #{userId} AND is_default = 1 LIMIT 1")
    FavoriteFolder selectDefaultFolder(@Param("userId") long userId);

    /** API-IN10 删夹第一步：夹内 favorite 批量移入默认夹（collect_count 不参与，ADR-20） */
    @Update("UPDATE favorite SET folder_id = #{toFolderId} " +
            "WHERE folder_id = #{fromFolderId} AND user_id = #{userId} AND deleted = 0")
    int moveFavoritesTo(@Param("userId") long userId,
                        @Param("fromFolderId") long fromFolderId,
                        @Param("toFolderId") long toFolderId);
}
