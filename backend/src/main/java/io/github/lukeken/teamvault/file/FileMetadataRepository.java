package io.github.lukeken.teamvault.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {
    List<FileMetadata> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<FileMetadata> findByIdAndCompanyId(UUID id, UUID companyId);
}
