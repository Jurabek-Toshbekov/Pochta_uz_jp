package uz.pochtajp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * post_categories jadvalining kompozit kaliti.
 */
@Embeddable
public class PostCategoryId implements Serializable {

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "category_id", nullable = false)
    private Short categoryId;

    protected PostCategoryId() {
        // JPA uchun
    }

    public PostCategoryId(UUID postId, Short categoryId) {
        this.postId = postId;
        this.categoryId = categoryId;
    }

    public UUID getPostId() {
        return postId;
    }

    public Short getCategoryId() {
        return categoryId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PostCategoryId other)) {
            return false;
        }
        return Objects.equals(postId, other.postId)
                && Objects.equals(categoryId, other.categoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, categoryId);
    }
}
