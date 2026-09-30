package edu.lms.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;


@Entity
@Table(name = "payment_method", indexes = @Index(columnList = "username"))
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore   // always taken from the caller, never from the request body
    @Column(nullable = false)
    private String username;

    @NotBlank
    @Column(nullable = false)
    private String brand;

    @Pattern(regexp = "\\d{4}", message = "last4 must be exactly 4 digits")
    @Column(nullable = false)
    private String last4;

    @Pattern(regexp = "(0[1-9]|1[0-2])/\\d{4}", message = "expires must look like 08/2028")
    @Column(nullable = false)
    private String expires;

    // Named defaultMethod in Java (so Spring Data queries parse cleanly),
    // is_default in the database, isDefault in the JSON.
    @Column(name = "is_default", nullable = false)
    @JsonProperty("isDefault")
    private boolean defaultMethod;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getLast4() { return last4; }
    public void setLast4(String last4) { this.last4 = last4; }
    public String getExpires() { return expires; }
    public void setExpires(String expires) { this.expires = expires; }
    @JsonProperty("isDefault")
    public boolean isDefaultMethod() { return defaultMethod; }
    @JsonProperty("isDefault")
    public void setDefaultMethod(boolean defaultMethod) { this.defaultMethod = defaultMethod; }
}
