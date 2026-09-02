package uz.pochtajp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.pochtajp.domain.SearchQuery;

@Repository
public interface SearchQueryRepository extends JpaRepository<SearchQuery, Long> {
}
