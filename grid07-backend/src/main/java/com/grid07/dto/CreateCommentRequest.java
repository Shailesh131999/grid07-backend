package com.grid07.dto;

import com.grid07.entity.Post;
import lombok.Data;

@Data
public class CreateCommentRequest {
    private Long authorId;
    private Post.AuthorType authorType; // USER or BOT
    private String content;

    // optional - if replying to another comment, send parent comment id
    // if null, treated as top-level comment on the post
    private Long parentCommentId;

    // only needed when bot is commenting - used for cooldown check
    private Long targetUserId;
}
