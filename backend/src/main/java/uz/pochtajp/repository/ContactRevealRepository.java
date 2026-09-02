package uz.pochtajp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.ContactReveal;

@Repository
public interface ContactRevealRepository extends JpaRepository<ContactReveal, Long> {

    /** Bir foydalanuvchi bir e'londa bir marta hisoblanadi (V4 UNIQUE indeksi). */
    boolean existsByPost_IdAndViewer_Id(java.util.UUID postId, java.util.UUID viewerId);

    /**
     * E'londa kontakt ochgan odamlar — eng oxirgisi birinchi.
     *
     * <p>Bitim kim bilan bo'lganini aniq bilib bo'lmaydi. Agar faqat bitta
     * odam kontakt ochgan bo'lsa — katta ehtimol bilan o'sha. Bir nechta
     * bo'lsa taxmin qilinmaydi va sherik {@code null} qoladi (§5.1 ruhi:
     * noaniq ma'lumot yozilmaydi).
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT r.viewer.id FROM ContactReveal r
            WHERE r.post.id = :postId
            ORDER BY r.createdAt DESC
            """)
    java.util.List<java.util.UUID> findViewerIds(
            @org.springframework.data.repository.query.Param("postId") java.util.UUID postId);
}
