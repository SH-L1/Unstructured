package egovframework.example.complaint.repository;

import egovframework.example.complaint.domain.Department;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

	Optional<Department> findByCode(String code);

	boolean existsByCode(String code);
}
