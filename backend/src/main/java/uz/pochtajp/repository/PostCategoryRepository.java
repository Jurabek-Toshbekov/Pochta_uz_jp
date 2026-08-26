package uz.pochtajp.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.PostCategory;
import uz.pochtajp.domain.PostCategoryId;

@Repository
public interface PostCategoryRepository extends JpaRepository<PostCategory, PostCategoryId> {

    /** E'lonning kategoriya ID'lari — DTO va kanal shabloni uchun. */
    @Query("""
            SELECT pc.id.categoryId FROM PostCategory pc
            WHERE pc.id.postId = :postId
            ORDER BY pc.id.categoryId
            """)
    List<Short> findCategoryIdsByPostId(@Param("postId") UUID postId);

    @Query("""
            SELECT pc FROM PostCategory pc JOIN FETCH pc.category
            WHERE pc.id.postId IN :postIds
            """)
    List<PostCategory> findWithCategoryByPostIds(@Param("postIds") Collection<UUID> postIds);
}
