package uz.pochtajp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.Corridor;

@Repository
public interface CorridorRepository extends JpaRepository<Corridor, Short> {
    Optional<Corridor> findByCode(String code);
}
