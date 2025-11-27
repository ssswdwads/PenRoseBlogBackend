package com.kirisamemarisa.blog.mapper;

import com.kirisamemarisa.blog.dto.BlogPostCreateDTO;
import com.kirisamemarisa.blog.dto.BlogPostDTO;
import com.kirisamemarisa.blog.dto.BlogPostUpdateDTO;
import com.kirisamemarisa.blog.model.BlogPost;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface BlogPostMapper {
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
        @Mapping(target = "updatedAt", ignore = true)
    })
    BlogPost toEntity(BlogPostCreateDTO dto);

    @Mappings({
        @Mapping(target = "userId", source = "user.id"),
        @Mapping(target = "originalPostId", source = "originalPost.id", ignore = true),
        @Mapping(target = "likedByCurrentUser", ignore = true)
    })
    BlogPostDTO toDTO(BlogPost entity);

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
        @Mapping(target = "updatedAt", ignore = true)
    })
    void updateEntityFromDTO(BlogPostUpdateDTO dto, @MappingTarget BlogPost entity);
}
