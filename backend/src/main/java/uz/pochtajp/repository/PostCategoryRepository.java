package uz.pochtajp.repository;

import uz.pochtajp.domain.PostCategoryId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.PostCategory;

@Repository
public interface PostCategoryRepository extends JpaRepository<PostCategory, PostCategoryId> {
}
