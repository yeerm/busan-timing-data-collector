package com.busantiming.sync;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "content_type_mappings", schema = "busan_timing_api")
@Getter
@NoArgsConstructor
public class ContentTypeMapping {

    @Id
    @Column(name = "content_type_id")
    private Integer contentTypeId;

    @Column(nullable = false, length = 50)
    private String content;
}
