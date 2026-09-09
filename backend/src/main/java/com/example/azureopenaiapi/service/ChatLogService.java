package com.example.azureopenaiapi.service;

import java.util.List;
import com.example.azureopenaiapi.entity.ChatLogEntity;

public interface ChatLogService {

	List<ChatLogEntity> getAllChatLogEntity();

	List<ChatLogEntity> getChatLogsByEmployee(String employeeId);

	void saveChatLog(String employeeId, String question, String answer);

}