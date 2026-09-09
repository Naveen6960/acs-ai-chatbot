package com.example.azureopenaiapi.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.azureopenaiapi.entity.ChatLogEntity;
import com.example.azureopenaiapi.repository.ChatLogEntityRepository;
import com.example.azureopenaiapi.service.ChatLogService;

@Service
public class ChatLogServiceImpl implements ChatLogService {

	private static final Logger log = LoggerFactory.getLogger(ChatLogServiceImpl.class);

	@Value("${acs.numOfRecents}")
	private Integer numOfRecents;

	@Autowired
	private ChatLogEntityRepository chatLogEntityRepository;

	@Override
	public List<ChatLogEntity> getAllChatLogEntity(){
		log.info("Fetching top {} recent chat log entities from database", numOfRecents);
		List<ChatLogEntity> chatLogEntityList = chatLogEntityRepository.findAllByOrderByLogIdDesc(PageRequest.of(0, numOfRecents));
		log.info("Total chat logs fetched: {}", chatLogEntityList.size());
		return chatLogEntityList;
	}

	@Override
	public List<ChatLogEntity> getChatLogsByEmployee(String employeeId) {
		log.info("Fetching top {} recent chat log entities for employee: {}", numOfRecents, employeeId);
		return chatLogEntityRepository.findByEmployeeIdOrderByLogIdDesc(employeeId, PageRequest.of(0, numOfRecents));
	}

	@Override
	public void saveChatLog(String employeeId, String question, String answer) {
		log.info("Saving chat log for employee: {}", employeeId); // NEW - log when save starts
		log.info("Question: {}", question); // NEW - log the question being saved
		ChatLogEntity log1 = new ChatLogEntity();
		log1.setEmployeeId(employeeId);
		log1.setQuestion(question != null && question.length() > 1000 ? question.substring(0, 1000) : question);
		log1.setAnswer(answer != null && answer.length() > 1000 ? answer.substring(0, 1000) : answer);
		log1.setCreationTimeStamp(java.time.LocalDateTime.now());
		chatLogEntityRepository.save(log1);
		log.info("Chat log saved successfully for employee: {}", employeeId); // NEW - log when save is complete
	}

}