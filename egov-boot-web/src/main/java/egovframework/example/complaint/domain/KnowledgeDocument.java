package egovframework.example.complaint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DocumentType documentType;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, length = 200)
	private String sourceName;

	@Column(length = 500)
	private String sourceUrl;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(nullable = false, length = 500)
	private String keywords;

	@Column(length = 500)
	private String legalBasis;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private KnowledgePurpose purpose = KnowledgePurpose.UNVERIFIED_LEGACY;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private KnowledgeVerificationStatus verificationStatus = KnowledgeVerificationStatus.UNVERIFIED_LEGACY;

	@Column(length = 80)
	private String jurisdictionCode;

	private LocalDate effectiveFrom;

	private LocalDate effectiveTo;

	@Column(length = 128)
	private String contentHash;

	@Column(length = 200)
	private String sourceVersion;

	protected KnowledgeDocument() {
	}

	public KnowledgeDocument(DocumentType documentType, String title, String sourceName, String sourceUrl,
			String content, String keywords, String legalBasis) {
		this.documentType = documentType;
		this.title = title;
		this.sourceName = sourceName;
		this.sourceUrl = sourceUrl;
		this.content = content;
		this.keywords = keywords;
		this.legalBasis = legalBasis;
	}

	public Long getId() {
		return id;
	}

	public DocumentType getDocumentType() {
		return documentType;
	}

	public String getTitle() {
		return title;
	}

	public String getSourceName() {
		return sourceName;
	}

	public String getContent() {
		return content;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public String getKeywords() {
		return keywords;
	}

	public String getLegalBasis() {
		return legalBasis;
	}

	public KnowledgePurpose getPurpose() {
		return purpose;
	}

	public KnowledgeVerificationStatus getVerificationStatus() {
		return verificationStatus;
	}

	public String getJurisdictionCode() {
		return jurisdictionCode;
	}

	public LocalDate getEffectiveFrom() {
		return effectiveFrom;
	}

	public LocalDate getEffectiveTo() {
		return effectiveTo;
	}

	public String getContentHash() {
		return contentHash;
	}

	public String getSourceVersion() {
		return sourceVersion;
	}

	public boolean isEligibleEvidence(LocalDate onDate) {
		boolean verified = verificationStatus == KnowledgeVerificationStatus.VERIFIED_OFFICIAL
				|| verificationStatus == KnowledgeVerificationStatus.VERIFIED_INTERNAL;
		boolean effective = (effectiveFrom == null || !effectiveFrom.isAfter(onDate))
				&& (effectiveTo == null || !effectiveTo.isBefore(onDate));
		return verified && effective;
	}

	public boolean isOfficialLegalEvidence(LocalDate onDate) {
		return purpose == KnowledgePurpose.OFFICIAL_LAW
				&& verificationStatus == KnowledgeVerificationStatus.VERIFIED_OFFICIAL
				&& jurisdictionCode != null
				&& jurisdictionCode.equalsIgnoreCase("NATIONAL")
				&& sourceVersion != null
				&& !sourceVersion.isBlank()
				&& contentHash != null
				&& !contentHash.isBlank()
				&& isEligibleEvidence(onDate);
	}

	public void verifyForTest(KnowledgePurpose purpose, KnowledgeVerificationStatus verificationStatus,
			String jurisdictionCode, LocalDate effectiveFrom, LocalDate effectiveTo) {
		this.purpose = purpose;
		this.verificationStatus = verificationStatus;
		this.jurisdictionCode = jurisdictionCode;
		this.effectiveFrom = effectiveFrom;
		this.effectiveTo = effectiveTo;
		this.sourceVersion = "SYNTHETIC_TEST_V1";
		this.contentHash = sha256(content);
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
			);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
