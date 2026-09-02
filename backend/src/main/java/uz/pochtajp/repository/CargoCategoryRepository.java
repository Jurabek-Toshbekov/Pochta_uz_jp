package uz.pochtajp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.CargoCategory;

@Repository
public interface CargoCategoryRepository extends JpaRepository<CargoCategory, Short> {
    List<CargoCategory> findByActiveTrueOrderBySortOrderAsc();

    Optional<CargoCategory> findByCode(String code);
}
