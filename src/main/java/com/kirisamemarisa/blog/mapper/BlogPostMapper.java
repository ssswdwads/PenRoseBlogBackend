package com.kirisamemarisa.blog.mapper;

import com.kirisamemarisa.blog.dto.BlogPostCreateDTO;
import com.kirisamemarisa.blog.dto.BlogPostDTO;
import com.kirisamemarisa.blog.dto.BlogPostUpdateDTO;
import com.kirisamemarisa.blog.model.BlogPost;
import com.kirisamemarisa.blog.model.UserProfile;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
@SuppressWarnings("unused")
public interface BlogPostMapper {
    @Mappings({
        // map title from DTO to entity
        @Mapping(target = "title", source = "title"),
        @Mapping(target = "user", ignore = true),
        @Mapping(target = "likeCount", ignore = true),
        @Mapping(target = "commentCount", ignore = true),
        @Mapping(target = "shareCount", ignore = true),
        @Mapping(target = "repostCount", ignore = true),
        @Mapping(target = "repost", ignore = true),
        @Mapping(target = "originalPost", ignore = true),
        @Mapping(target = "comments", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true)
    })
    BlogPost toEntity(BlogPostCreateDTO dto);

    @Mappings({
        @Mapping(target = "userId", source = "user.id"),
        @Mapping(target = "originalPostId", source = "originalPost.id", ignore = true),
        @Mapping(target = "likedByCurrentUser", ignore = true),
        // these author fields are populated by toDTOWithProfile when profile is available
        @Mapping(target = "authorNickname", ignore = true),
        @Mapping(target = "authorAvatarUrl", ignore = true),
        // map title from entity to DTO
        @Mapping(target = "title", source = "title")
    })
    BlogPostDTO toDTO(BlogPost entity);

    default BlogPostDTO toDTOWithProfile(BlogPost entity, UserProfile profile) {
        BlogPostDTO dto = toDTO(entity);
        if (dto == null) return null;
        if (profile != null) {
            dto.setAuthorNickname(profile.getNickname());
            dto.setAuthorAvatarUrl(profile.getAvatarUrl());
        } else {
            // fallback: if profile missing, try to set nickname from User.username
            if (entity != null && entity.getUser() != null && entity.getUser().getUsername() != null) {
                dto.setAuthorNickname(entity.getUser().getUsername());
            } else {
                dto.setAuthorNickname("");
            }
            dto.setAuthorAvatarUrl("");
        }
        return dto;
    }

    @Mappings({
        @Mapping(target = "user", ignore = true),
        @Mapping(target = "likeCount", ignore = true),
        @Mapping(target = "commentCount", ignore = true),
        @Mapping(target = "shareCount", ignore = true),
        @Mapping(target = "repostCount", ignore = true),
        @Mapping(target = "repost", ignore = true),
        @Mapping(target = "originalPost", ignore = true),
        @Mapping(target = "comments", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "updatedAt", ignore = true),
        // map updatable fields from BlogPostUpdateDTO
        @Mapping(target = "coverImageUrl", source = "coverImageUrl"),
        @Mapping(target = "content", source = "content"),
        @Mapping(target = "directory", source = "directory")
    })
    void updateEntityFromDTO(BlogPostUpdateDTO dto, @MappingTarget BlogPost entity);
}
