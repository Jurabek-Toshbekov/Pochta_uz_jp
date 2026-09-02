package uz.pochtajp.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.MapsId;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * E'lon ↔ yuk kategoriyasi bog'lanishi (ko'pdan-ko'pga).
 */
@Entity
@Table(name = "post_categories")
public class PostCategory {

    @EmbeddedId
    private PostCategoryId id;

    @MapsId("postId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @MapsId("categoryId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private CargoCategory category;

    protected PostCategory() {
        // JPA uchun
    }

    public PostCategory(Post post, CargoCategory category) {
        this.post = post;
        this.category = category;
        this.id = new PostCategoryId(post.getId(), category.getId());
    }

    public PostCategoryId getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public CargoCategory getCategory() {
        return category;
    }
}
