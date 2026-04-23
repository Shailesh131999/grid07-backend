package com.grid07.dto;

import lombok.Data;

@Data
public class LikeRequest {
    private Long userId; // only human likes count toward virality
}
