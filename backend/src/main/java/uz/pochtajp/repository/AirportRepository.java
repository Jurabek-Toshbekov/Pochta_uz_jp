package uz.pochtajp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.Airport;

@Repository
public interface AirportRepository extends JpaRepository<Airport, String> {
    List<Airport> findByActiveTrueOrderByCountryCodeAscSortOrderAsc();
}
