package com.grid07.service;

import com.grid07.dto.CreatePostRequest;
import com.grid07.entity.Post;
import com.grid07.exception.GuardrailException;
import com.grid07.repository.BotRepository;
import com.grid07.repository.PostRepository;
import com.grid07.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final BotRepository botRepository;
    private final ViralityService viralityService;

    public PostService(PostRepository postRepository,
                       UserRepository userRepository,
                       BotRepository botRepository,
                       ViralityService viralityService) {
        this.postRepository   = postRepository;
        this.userRepository   = userRepository;
        this.botRepository    = botRepository;
        this.viralityService  = viralityService;
    }

    @Transactional
    public Post createPost(CreatePostRequest req) {
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new IllegalArgumentException("Post content cannot be empty.");
        }

        // make sure the author actually exists
        validateAuthorExists(req.getAuthorId(), req.getAuthorType());

        Post post = new Post();
        post.setAuthorId(req.getAuthorId());
        post.setAuthorType(req.getAuthorType());
        post.setContent(req.getContent().trim());

        return postRepository.save(post);
    }

    @Transactional
    public void likePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new GuardrailException("Post not found: " + postId, HttpStatus.NOT_FOUND));

        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        // only human likes contribute to virality
        viralityService.recordHumanLike(postId);
    }

    private void validateAuthorExists(Long authorId, Post.AuthorType type) {
        if (type == Post.AuthorType.USER) {
            if (!userRepository.existsById(authorId)) {
                throw new IllegalArgumentException("User not found: " + authorId);
            }
        } else {
            if (!botRepository.existsById(authorId)) {
                throw new IllegalArgumentException("Bot not found: " + authorId);
            }
        }
    }
}
