package com.klp.medicinebox.repository;

import com.klp.medicinebox.entity.DosageEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DosageRepository extends JpaRepository<DosageEntity, Long> {
    @Query(value = "SELECT * FROM dosage WHERE user_id = :uid", nativeQuery = true)
    List<DosageEntity> findByUid(String uid);
    
    DosageEntity findByDid(Long did);
}
