package japan.it.telegram_bot_dars.repository;


import japan.it.telegram_bot_dars.entity.PostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<PostEntity, Long> {
    Optional<PostEntity>findByChatId(String chatId);
    Optional<PostEntity> findTopByChatIdOrderByCreatedAtDesc(String chatId);
}
