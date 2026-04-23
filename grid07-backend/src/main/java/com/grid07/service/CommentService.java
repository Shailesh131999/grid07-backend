package com.grid07.service;

import com.grid07.dto.CreateCommentRequest;
import com.grid07.entity.Comment;
import com.grid07.entity.Post;
import com.grid07.exception.GuardrailException;
import com.grid07.repository.BotRepository;
import com.grid07.repository.CommentRepository;
import com.grid07.repository.PostRepository;
import com.grid07.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final CommentRepository commentRepository;
    private final PostRepository    postRepository;
    private final UserRepository    userRepository;
    private final BotRepository     botRepository;
    private final GuardrailService  guardrailService;
    private final ViralityService   viralityService;
    private final NotificationService notificationService;

    public CommentService(CommentRepository commentRepository,
                          PostRepository postRepository,
                          UserRepository userRepository,
                          BotRepository botRepository,
                          GuardrailService guardrailService,
                          ViralityService viralityService,
                          NotificationService notificationService) {
        this.commentRepository   = commentRepository;
        this.postRepository      = postRepository;
        this.userRepository      = userRepository;
        this.botRepository       = botRepository;
        this.guardrailService    = guardrailService;
        this.viralityService     = viralityService;
        this.notificationService = notificationService;
    }

    @Transactional
    public Comment addComment(Long postId, CreateCommentRequest req) {
        // --- basic validation ---
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new IllegalArgumentException("Comment content cannot be empty.");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new GuardrailException("Post not found: " + postId, HttpStatus.NOT_FOUND));

        // figure out depth - top level = 0, replying to a comment = parent depth + 1
        int depthLevel = 0;
        if (req.getParentCommentId() != null) {
            Comment parent = commentRepository.findById(req.getParentCommentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent comment not found: " + req.getParentCommentId()));

            if (!parent.getPostId().equals(postId)) {
                throw new IllegalArgumentException("Parent comment does not belong to this post.");
            }
            depthLevel = parent.getDepthLevel() + 1;
        }

        boolean isBotComment = req.getAuthorType() == Post.AuthorType.BOT;

        // --- run guardrails for bot comments ---
        if (isBotComment) {
            // 1. vertical cap - don't allow threads deeper than 20
            guardrailService.checkDepthLimit(depthLevel);

            // 2. horizontal cap - max 100 bot replies per post (atomic INCR)
            guardrailService.checkAndIncrementBotCount(postId);

            // 3. cooldown cap - bot can't spam the same human within 10 min
            if (req.getTargetUserId() != null) {
                try {
                    guardrailService.checkBotCooldown(req.getAuthorId(), req.getTargetUserId());
                } catch (GuardrailException e) {
                    // cooldown blocked us, roll back the bot count we just incremented
                    guardrailService.rollbackBotCount(postId);
                    throw e;
                }
            }
        }

        // build and save the comment
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(req.getAuthorId());
        comment.setAuthorType(req.getAuthorType());
        comment.setContent(req.getContent().trim());
        comment.setDepthLevel(depthLevel);
        comment.setParentCommentId(req.getParentCommentId());

        Comment saved;
        try {
            saved = commentRepository.save(comment);
        } catch (Exception e) {
            // DB save failed after we already claimed a bot slot - roll it back
            if (isBotComment) {
                guardrailService.rollbackBotCount(postId);
            }
            throw e;
        }

        // --- update virality + send notifications ---
        if (isBotComment) {
            viralityService.recordBotReply(postId);

            // notify the post author if they're a human
            if (post.getAuthorType() == Post.AuthorType.USER) {
                String botName = botRepository.findById(req.getAuthorId())
                        .map(b -> b.getName())
                        .orElse("Unknown Bot");
                notificationService.handleBotInteractionNotification(
                        post.getAuthorId(), botName, "replied to your post"
                );
            }
        } else {
            // human comment
            viralityService.recordHumanComment(postId);
        }

        log.info("Comment saved: id={} postId={} authorType={} depth={}", saved.getId(), postId, req.getAuthorType(), depthLevel);
        return saved;
    }
}
