package com.grid07.controller;

import com.grid07.dto.ApiResponse;
import com.grid07.dto.CreateCommentRequest;
import com.grid07.dto.CreatePostRequest;
import com.grid07.dto.LikeRequest;
import com.grid07.entity.Comment;
import com.grid07.entity.Post;
import com.grid07.service.CommentService;
import com.grid07.service.PostService;
import com.grid07.service.ViralityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService     postService;
    private final CommentService  commentService;
    private final ViralityService viralityService;

    public PostController(PostService postService,
                          CommentService commentService,
                          ViralityService viralityService) {
        this.postService     = postService;
        this.commentService  = commentService;
        this.viralityService = viralityService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Post>> createPost(@RequestBody CreatePostRequest req) {
        Post post = postService.createPost(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Post created successfully", post));
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<Comment>> addComment(
            @PathVariable Long postId,
            @RequestBody CreateCommentRequest req) {

        Comment comment = commentService.addComment(postId, req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Comment added", comment));
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<ApiResponse<Map<String, Object>>> likePost(
            @PathVariable Long postId,
            @RequestBody LikeRequest req) {

        postService.likePost(postId, req.getUserId());

        long viralityScore = viralityService.getViralityScore(postId);
        return ResponseEntity.ok(ApiResponse.ok("Post liked", Map.of(
                "postId", postId,
                "viralityScore", viralityScore
        )));
    }

    // handy debug endpoint to check virality score without doing anything
    @GetMapping("/{postId}/virality")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getViralityScore(@PathVariable Long postId) {
        long score = viralityService.getViralityScore(postId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("postId", postId, "viralityScore", score)));
    }
}
