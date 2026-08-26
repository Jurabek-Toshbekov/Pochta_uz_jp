package uz.pochtajp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.ContactReveal;

@Repository
public interface ContactRevealRepository extends JpaRepository<ContactReveal, Long> {
}
