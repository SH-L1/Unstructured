package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.KnowledgeDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

	boolean existsByTitle(String title);

	Optional<KnowledgeDocument> findByTitle(String title);

	@Query("""
			select k
			from KnowledgeDocument k
			where lower(k.keywords) like lower(concat('%', :keyword, '%'))
			   or lower(k.content) like lower(concat('%', :keyword, '%'))
			order by
			  case when lower(k.title) = lower(:keyword) then 0
			       when lower(k.title) like lower(concat('%', :keyword, '%')) then 1
			       when lower(k.keywords) like lower(concat('%', :keyword, '%')) then 2
			       else 3
			  end,
			  k.id
			""")
	List<KnowledgeDocument> searchByKeyword(@Param("keyword") String keyword);
}
