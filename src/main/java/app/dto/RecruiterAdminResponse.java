package app.dto;

public record RecruiterAdminResponse(Long id, String name, String email, String companyName,
                                     String industry, String website, String location,
                                     boolean verified, boolean enabled) {}
