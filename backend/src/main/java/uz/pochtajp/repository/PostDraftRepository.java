package uz.pochtajp.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.PostDraft;

@Repository
public interface PostDraftRepository extends JpaRepository<PostDraft, UUID> {
    Optional<PostDraft> findByUser_Id(UUID userId);
}
