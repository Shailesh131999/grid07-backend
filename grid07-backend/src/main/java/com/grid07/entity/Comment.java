package com.grid07.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false)
    private Post.AuthorType authorType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    // depth 0 means top-level comment, 1 means reply to a comment, etc.
    @Column(name = "depth_level")
    private int depthLevel = 0;

    // null if this is a top-level comment
    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
