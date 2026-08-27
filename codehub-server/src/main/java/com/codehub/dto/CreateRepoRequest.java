package com.codehub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateRepoRequest {
    @NotBlank(message = "仓库名不能为空")
    @Size(min = 1, max = 100, message = "仓库名长度1-100个字符")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "仓库名只允许字母、数字、横杠、下划线")
    private String name;

    private String description;

    @Pattern(regexp = "^(PUBLIC|PRIVATE)$", message = "可见性只能是PUBLIC或PRIVATE")
    private String visibility = "PRIVATE";
}
