package uz.pochtajp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.ContactReveal;

@Repository
public interface ContactRevealRepository extends JpaRepository<ContactReveal, Long> {

    /** Bir foydalanuvchi bir e'londa bir marta hisoblanadi (V4 UNIQUE indeksi). */
    boolean existsByPost_IdAndViewer_Id(java.util.UUID postId, java.util.UUID viewerId);
}
