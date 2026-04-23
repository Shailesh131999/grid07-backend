package com.grid07.dto;

import com.grid07.entity.Post;
import lombok.Data;

@Data
public class CreatePostRequest {
    private Long authorId;
    private Post.AuthorType authorType; // USER or BOT
    private String content;
}
