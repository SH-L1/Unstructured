package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.ComplaintSensitivePayload;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ComplaintSensitivePayloadRepository extends JpaRepository<ComplaintSensitivePayload, UUID> {

	@Query("select payload.rawStorageReference from ComplaintSensitivePayload payload")
	List<String> findAllStorageReferences();
}
