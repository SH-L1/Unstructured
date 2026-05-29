package com.school.complaint.repository;

import com.school.complaint.domain.KnowledgeDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

	boolean existsByTitle(String title);

	@Query("""
			select k
			from KnowledgeDocument k
			where lower(k.keywords) like lower(concat('%', :keyword, '%'))
			   or lower(k.content) like lower(concat('%', :keyword, '%'))
			""")
	List<KnowledgeDocument> searchByKeyword(@Param("keyword") String keyword);
}
