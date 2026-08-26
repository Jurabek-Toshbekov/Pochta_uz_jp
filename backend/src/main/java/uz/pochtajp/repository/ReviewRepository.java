package uz.pochtajp.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.Review;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {
}
