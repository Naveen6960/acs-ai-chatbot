package com.example.azureopenaiapi.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.transaction.Transactional;
import com.example.azureopenaiapi.entity.ChatLogEntity;
import java.util.List;

@Repository
@Transactional
public interface ChatLogEntityRepository extends JpaRepository<ChatLogEntity, Integer>{

    // NAVEEN - fetches all chat logs ordered by latest first, limited by acs.numOfRecents
    List<ChatLogEntity> findAllByOrderByLogIdDesc(Pageable pageable);

    // NAVEEN - fetches chat logs for a specific employee ordered by latest first
    List<ChatLogEntity> findByEmployeeIdOrderByLogIdDesc(String employeeId, Pageable pageable);

}