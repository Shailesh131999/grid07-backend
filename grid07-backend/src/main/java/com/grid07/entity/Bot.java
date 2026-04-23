package com.grid07.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bots")
@Getter
@Setter
@NoArgsConstructor
public class Bot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "persona_description", columnDefinition = "TEXT")
    private String personaDescription;

    public Bot(String name, String personaDescription) {
        this.name = name;
        this.personaDescription = personaDescription;
    }
}
