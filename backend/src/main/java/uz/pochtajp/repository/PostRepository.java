package uz.pochtajp.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.Post;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    Optional<Post> findByIdAndDeletedAtIsNull(UUID id);

    /** Kunlik e'lon chegarasi uchun (§7.2 — 5 e'lon/kun). */
    long countByUser_IdAndCreatedAtAfterAndDeletedAtIsNull(UUID userId, Instant since);

    /** "Mening e'lonlarim" — eng yangisi tepada. */
    List<Post> findByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);
}
