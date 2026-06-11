package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.KnowledgeDocumentChunk;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeDocumentChunkRepository extends JpaRepository<KnowledgeDocumentChunk, Long> {

	boolean existsByKnowledgeDocumentIdAndChunkIndex(Long knowledgeDocumentId, int chunkIndex);

	List<KnowledgeDocumentChunk> findByKnowledgeDocumentIdOrderByChunkIndex(Long knowledgeDocumentId);

	@Query("""
			select c
			from KnowledgeDocumentChunk c
			join fetch c.knowledgeDocument k
			where c.active = true
			  and (
			      lower(c.keywords) like lower(concat('%', :keyword, '%'))
			      or lower(c.content) like lower(concat('%', :keyword, '%'))
			      or lower(k.keywords) like lower(concat('%', :keyword, '%'))
			  )
			order by
			  case when lower(k.title) = lower(:keyword) then 0
			       when lower(k.title) like lower(concat('%', :keyword, '%')) then 1
			       when lower(c.keywords) like lower(concat('%', :keyword, '%')) then 2
			       else 3
			  end,
			  c.chunkIndex
			""")
	List<KnowledgeDocumentChunk> searchByKeyword(@Param("keyword") String keyword);
}
