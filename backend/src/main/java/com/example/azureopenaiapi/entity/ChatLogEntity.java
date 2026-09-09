package com.example.azureopenaiapi.entity;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name="chatlog")
public class ChatLogEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "logid", nullable = false)
	private Long logId;

	@Column(name = "employee_id")
	private String employeeId;

	@Column(name = "creation_time_stamp")
	private LocalDateTime creationTimeStamp;

	@Column(name = "question")
	private String question;

	@Column(name = "answer")
	private String answer;
}