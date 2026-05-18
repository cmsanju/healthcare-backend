package com.healthcare.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;
    private String originalFilename;
    private String filePath;
    private String fileType;
    private Long fileSize;
    private String status; // UPLOADED, ANALYZING, ANALYZED, FAILED

    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @Column(columnDefinition = "TEXT")
    private String analysisResult;

    @Column(columnDefinition = "TEXT")
    private String suggestions;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDateTime uploadedAt = LocalDateTime.now();
    private LocalDateTime analyzedAt;
}
