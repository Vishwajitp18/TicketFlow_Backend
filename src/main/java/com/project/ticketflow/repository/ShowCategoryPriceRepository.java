package com.project.ticketflow.repository;

import com.project.ticketflow.entity.Show;
import com.project.ticketflow.entity.ShowCategoryPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShowCategoryPriceRepository extends JpaRepository<ShowCategoryPrice, Long> {
    List<ShowCategoryPrice> findByShow(Show show);
}
