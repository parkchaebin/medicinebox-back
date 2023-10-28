package com.klp.medicinebox.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.klp.medicinebox.dto.DrugDTO;
import com.klp.medicinebox.dto.ShapeDTO;
import com.klp.medicinebox.entity.DrugEntity;
import com.klp.medicinebox.entity.ShapeEntity;
import com.klp.medicinebox.repository.DrugRepository;
import com.klp.medicinebox.repository.ShapeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrugService {

    private final DrugRepository drugRepository;
    private final ShapeRepository shapeRepository;

    @Value("${medicineShape.folder}")
    private String medicineShapeFolderPath;

    @Autowired
    private ServletContext ctx;
    
    /**
     * 제품 검색을 위한 함수
     *
     * @param type 검색 방법에 대한 타입(1: 제품코드, 2: 제품명, 3: 효능관련)
     * @param search 검색한 제품 명
     * @return 검색된 리스트를 반환
     * @author 박채빈
     *
     */
    public List<DrugDTO> searchDrugList(int type, String search) {
        List<DrugDTO> drugDTOS = new ArrayList<>();
        try {
            /*URL*/
            StringBuilder urlBuilder = new StringBuilder("http://apis.data.go.kr/1471000/DrbEasyDrugInfoService/getDrbEasyDrugList");
            /*Service Key*/
            urlBuilder.append("?" + URLEncoder.encode("serviceKey", "UTF-8") + "=Luco9rwVuxP3RyPaO%2BYc09eiRfSRf%2B260CwkIJfvChXaraDw6TkGMGAO2XeHGX%2FIzhlKjnDgLx60xGKd2UYh1Q%3D%3D");
            /*페이지번호*/
            urlBuilder.append("&" + URLEncoder.encode("pageNo", "UTF-8") + "=" + URLEncoder.encode("1", "UTF-8"));
            /*한 페이지 결과 수*/
            urlBuilder.append("&" + URLEncoder.encode("numOfRows", "UTF-8") + "=" + URLEncoder.encode("100", "UTF-8"));
            
            if (type == 1) {
                /* 품목기준코드 */
                urlBuilder.append("&" + URLEncoder.encode("itemSeq", "UTF-8") + "=" + URLEncoder.encode(search, "UTF-8"));
            } else if (type == 2) {/* 제품명 */
                urlBuilder.append("&" + URLEncoder.encode("itemName", "UTF-8") + "=" + URLEncoder.encode(search, "UTF-8"));
            } else if (type == 3) {/* 효능 */
                urlBuilder.append("&" + URLEncoder.encode("efcyQesitm", "UTF-8") + "=" + URLEncoder.encode(search, "UTF-8"));
                /*이 약의 효능은 무엇입니까?*/
            }
            
            urlBuilder.append("&" + URLEncoder.encode("type", "UTF-8") + "=" + URLEncoder.encode("json", "UTF-8"));
            /*응답데이터 형식(xml/json) Default:xml*/
            
            URL url = new URL(urlBuilder.toString());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-type", "application/json");
            conn.setRequestProperty("Accept-Charset", "UTF-8");
            
            BufferedReader rd;
            if (conn.getResponseCode() >= 200 && conn.getResponseCode() <= 300) {
                rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            } else {
                rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            }
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                sb.append(line);
            }
            rd.close();
            conn.disconnect();
            
            String jsonResponse = sb.toString();
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonResponse);
            
            JsonNode bodyNode = rootNode.path("body");
            if (!bodyNode.isMissingNode()) {
                JsonNode itemsArray = bodyNode.path("items");
                
                for (JsonNode item : itemsArray) {
                    DrugDTO drugDTO = DrugDTO.builder()
                            .seq(item.get("itemSeq").asText())
                            .entpName(item.get("entpName").asText())
                            .name(item.get("itemName").asText())
                            .efcy(item.get("efcyQesitm").asText())
                            .use(item.get("useMethodQesitm").asText())
                            .atpn(item.get("atpnQesitm").asText())
                            .atpnWarn(item.get("atpnWarnQesitm").asText())
                            .intrc(item.get("intrcQesitm").asText())
                            .se(item.get("seQesitm").asText())
                            .diposit(item.get("depositMethodQesitm").asText())
                            .image(item.get("itemImage").asText())
                            .build();
                    drugDTOS.add(drugDTO);
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(DrugService.class.getName()).log(Level.SEVERE, null, ex);
        }
        return drugDTOS;
    }

    
    
    /**
     * 약품의 모양으로 검색
     *
     * @param shapeDTO 검색할 모양 입력 정보
     * @return 관련 약품 리스트
     * @author 박채빈
     *
     */
    public List<ShapeDTO> getDrugFromShape(ShapeDTO shapeDTO) {
        
       List<ShapeDTO> shapeDTOs = new ArrayList<>();
        // 1. 검색창에 입력된 모양으로 검색(shapeEntity)
        
        if (shapeDTO.getForm() != null && shapeDTO.getForm().equals("정제")) {
            shapeDTO.setForm("정");
        }

        if (shapeDTO.getFrontLine() != null && shapeDTO.getFrontLine().equals("없음")) {
            shapeDTO.setFrontLine("");
        }

        List<ShapeEntity> shapeEntities = shapeRepository.findSeqByShape(shapeDTO.getShape(), shapeDTO.getForm(), shapeDTO.getFrontLine(), shapeDTO.getFrontColor(), shapeDTO.getBackColor(), shapeDTO.getFrontPrint(), shapeDTO.getBackPrint());

        // 2. 검색한 결과들의 제품 기준 코드, 이름, 사진 반환 (shapeDTO)
        for (ShapeEntity shapeEntity : shapeEntities) {

                ShapeDTO searchResult = new ShapeDTO();
                searchResult.setSeq(shapeEntity.getSeq());
                searchResult.setName(shapeEntity.getName());
                searchResult.setImage(shapeEntity.getImage());

                shapeDTOs.add(searchResult);
            }

        return shapeDTOs;
    }
    
    

    /**
     * 선택한 제품의 정보를 반환
     *
     * @param seq 선택한 제품의 제품 코드
     * @return 선택된 제품의 정보
     * @author 박채빈
     *
     */
    public DrugDTO getDrugInfo(String seq) {
        // 1. 제품 코드를 이용하여 약품 검색
        List<DrugDTO> drugDTOS = searchDrugList(1, seq);
        
        // 2. 해당 제품의 정보를 반환
        if(drugDTOS.isEmpty()) {
            return null;
        } else{
            return drugDTOS.get(0);
        }
    }

    
    
    /**
     * 선택한 제품을 자신의 제품 리스트에 추가하는 함수
     *
     * @param drugDTO 제품 코드, 수량, 구매 일자, 사용 기한 입력 정보
     * @param uid 사용자 아이디
     * @return 등록 성공 여부
     * @author 박채빈
     *
     */
    public boolean addMyDrug(DrugDTO drugDTO, String uid) {
        // 1. 제품 정보를 통해 제품을 등록
        DrugEntity drugEntity = DrugEntity.builder()
                .uid(uid)
                .seq(drugDTO.getSeq())
                .entpName(drugDTO.getEntpName())
                .name(drugDTO.getName())
                .efcy(drugDTO.getEfcy())
                .use(drugDTO.getUse())
                .atpnWarn(drugDTO.getAtpnWarn())
                .atpn(drugDTO.getAtpn())
                .intrc(drugDTO.getIntrc())
                .se(drugDTO.getSe())
                .diposit(drugDTO.getDiposit())
                .image(drugDTO.getImage())
                .count(drugDTO.getCount())
                .buyDate(LocalDate.parse(drugDTO.getBuyDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .expirationDate(LocalDate.parse(drugDTO.getExpirationDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .registerDate(LocalDateTime.now())
                .updateDate(LocalDateTime.now())
                .build();
        
        // 2. 제품 등록 성공 여부를 반환
        return drugRepository.save(drugEntity) != null;
    }

    
    /**
     * 자신이 등록한 제품의 리스트를 가져오는 함수
     *
     * @param uid 사용자 아이디
     * @param filter 목록 정렬 필터
     * @return 요청한 아이디에 따른 등록 제품 리스트를 반환
     */
    public List<DrugDTO> getMyDrugList(String uid, String filter) {
        List<DrugDTO> drugDTOS = new ArrayList<>();
        
        // 1. 아이디에 해당하는 약품 목록(PossessionEntity)을 필터에 따라서(등록 순 - 최초 등록 registerDate순, 최신순 - 정보가 새로 등록된 updateDate순)으로 가져오기
        List<DrugEntity> drugEntities = null;
        
        if(filter.equals("등록순")) {  // 등록순 
            drugEntities = drugRepository.findByUidOrderByRegisterDate(uid);
        } else if(filter.equals("최신순")) {  // 최신순  
            drugEntities = drugRepository.findByUidOrderByUpdateDate(uid);
        }
        
        // 2. 받아온 리스트를 DTO 리스트로 변경해서 반환
        for (DrugEntity drugEntity : drugEntities) {
            
            drugDTOS.add(DrugDTO.builder()
                    .pid(drugEntity.getPid())
                    .seq(drugEntity.getSeq())
                    .entpName(drugEntity.getEntpName())
                    .name(drugEntity.getName())
                    .efcy(drugEntity.getEfcy())
                    .use(drugEntity.getUse())
                    .atpnWarn(drugEntity.getAtpnWarn())
                    .atpn(drugEntity.getAtpn())
                    .intrc(drugEntity.getIntrc())
                    .se(drugEntity.getSe())
                    .diposit(drugEntity.getDiposit())
                    .image(drugEntity.getImage())
                    .count(drugEntity.getCount())
                    .buyDate(drugEntity.getBuyDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                    .expirationDate(drugEntity.getExpirationDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                    .build());
        }

        return drugDTOS;
    }

    
    /**
     * 선택한 보유 제품의 정보를 반환
     *
     * @param pid 보유 제품 고유번호 
     * @param uid 사용자 아이디
     * @return 선택된 제품의 정보
     * @author 박채빈
     *
     */
    public DrugDTO getMyDrugInfo(Long pid, String uid) {
        // 1. 제품 코드를 이용하여 약품 검색
        // 2. 해당 제품의 정보(DrugEntity랑 PossessionEntity를 join)를 반환
        DrugEntity drugEntity = drugRepository.findByUidAndPid(pid, uid);

        DrugDTO drugDTO = DrugDTO.builder()
            .pid(drugEntity.getPid())
            .seq(drugEntity.getSeq())
            .entpName(drugEntity.getEntpName())
            .name(drugEntity.getName())
            .efcy(drugEntity.getEfcy())
            .use(drugEntity.getUse())
            .atpnWarn(drugEntity.getAtpnWarn())
            .atpn(drugEntity.getAtpn())
            .intrc(drugEntity.getIntrc())
            .se(drugEntity.getSe())
            .diposit(drugEntity.getDiposit())
            .image(drugEntity.getImage())
            .count(drugEntity.getCount())
            .buyDate(drugEntity.getBuyDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            .expirationDate(drugEntity.getExpirationDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            .build();
        
        return drugDTO;
    }

    
    /**
     * 선택한 보유 제품의 정보를 변경하는 함수
     *
     * @param drugDTO 제품 코드, 수량, 구매 일자, 사용 기한 입력 정보
     * @param uid 사용자 아이디
     * @return 수정 성공 여부
     * @author 박채빈
     *
     */
    public boolean updateMyDrug(DrugDTO drugDTO, String uid) {
        // 1. 제품 정보를 통해 제품을 등록(updateDate를 현재 시각으로 변경)
        DrugEntity drugEntity = drugRepository.findByUidAndPid(drugDTO.getPid(), uid);
            
        if (drugEntity != null) {
            drugEntity.setCount(drugDTO.getCount());
            drugEntity.setBuyDate(LocalDate.parse(drugDTO.getBuyDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            drugEntity.setExpirationDate(LocalDate.parse(drugDTO.getExpirationDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            drugEntity.setUpdateDate(LocalDateTime.now());

            // 2. 제품 등록 성공 여부를 반환   
            return drugRepository.save(drugEntity) != null;
        }
        return false;
    }

    
    /**
     * 선택한 제품을 자신의 제품 리스트에서 제거하는 함수
     *
     * @param pid 보유 제품 고유번호 
     * @param uid 사용자 아이디
     * @return 등록 성공 여부
     * @author 박채빈
     *
     */
    public boolean deleteMyDrug(Long pid, String uid) {
        // 1. 제품 정보를 통해 제품을 삭제
        DrugEntity drugEntity = drugRepository.findByUidAndPid(pid, uid);
        // 2. 제품 등록 성공 여부를 반환
        if (drugEntity != null) {
            drugRepository.delete(drugEntity);
            return true;
        }
        return false;
    }

    
    
    /**
     * medicine shape 엑셀 파일 데이터 DB에 저장 
     * @return 
     */
    public boolean addMedicineShape() {
        String directoryPath = ctx.getRealPath(medicineShapeFolderPath);
        List<ShapeEntity> shapeEntities = new ArrayList<>();

        File directory = new File(directoryPath);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();

            for (File file : files) {
                System.out.println("file = " + file);
                try (InputStream fis = new FileInputStream(file); Workbook workbook = WorkbookFactory.create(fis)) {
                    Sheet sheet = workbook.getSheetAt(0); 

                    for (Row row : sheet) {
                        if (row.getRowNum() == 0) {
                            // 헤더 값은 넣지 않음 
                            continue;
                        }

                        ShapeEntity shapeEntity = new ShapeEntity();
                        shapeEntity.setSeq(row.getCell(0).getStringCellValue()); // 0번째 열
                        shapeEntity.setName(row.getCell(1).getStringCellValue()); // 1번째 열
                        shapeEntity.setFrontPrint(row.getCell(6).getStringCellValue()); // 6번째 열
                        shapeEntity.setBackPrint(row.getCell(7).getStringCellValue()); // 7번째 열
                        shapeEntity.setShape(row.getCell(8).getStringCellValue()); // 8번째 열
                        shapeEntity.setFrontColor(row.getCell(9).getStringCellValue()); // 9번째 열
                        shapeEntity.setBackColor(row.getCell(10).getStringCellValue()); // 10번째 열
                        shapeEntity.setFrontLine(row.getCell(11).getStringCellValue()); // 11번째 열
                        shapeEntity.setBackLine(row.getCell(12).getStringCellValue()); // 12번째 열
                        shapeEntity.setForm(row.getCell(4).getStringCellValue()); // 4번째 열
                        shapeEntity.setImage(row.getCell(5).getStringCellValue()); // 5번째 열

                        shapeEntities.add(shapeEntity);
                    }
                } catch (Exception e) {
                    // 엑셀 파일 읽기 오류 처리
                    e.printStackTrace();
                }
            }

            shapeRepository.saveAll(shapeEntities);

            return true;
        }

        return false;
    }
    

    
    


//    public boolean addMedicineShape() {
//        String directoryPath = ctx.getRealPath(medicineShapeFolderPath);
//        List<ShapeEntity> shapeEntities = new ArrayList<>();
//
//        File directory = new File(directoryPath);
//        if (directory.exists() && directory.isDirectory()) {
//            File[] files = directory.listFiles();
//
//            for (File file : files) {
//                try {
//                    if (file.getName().toLowerCase().endsWith(".xls")) {
//
//                        try (InputStream fis = new FileInputStream(file); Workbook workbook = WorkbookFactory.create(fis)) {
//                            Sheet sheet = workbook.getSheetAt(0);
//
//                            for (Row row : sheet) {
//                                if (row.getRowNum() == 0) {
//                                    // 헤더 값은 넣지 않음
//                                    continue;
//                                }
//
//                                ShapeEntity shapeEntity = new ShapeEntity();
//                                shapeEntity.setSeq(row.getCell(0).getStringCellValue()); // 0번째 열
//                                shapeEntity.setName(row.getCell(1).getStringCellValue()); // 1번째 열
//                                shapeEntity.setFrontPrint(row.getCell(6).getStringCellValue()); // 6번째 열
//                                shapeEntity.setBackPrint(row.getCell(7).getStringCellValue()); // 7번째 열
//                                shapeEntity.setShape(row.getCell(8).getStringCellValue()); // 8번째 열
//                                shapeEntity.setFrontColor(row.getCell(9).getStringCellValue()); // 9번째 열
//                                shapeEntity.setBackColor(row.getCell(10).getStringCellValue()); // 10번째 열
//                                shapeEntity.setFrontLine(row.getCell(11).getStringCellValue()); // 11번째 열
//                                shapeEntity.setBackLine(row.getCell(12).getStringCellValue()); // 12번째 열
//                                shapeEntity.setForm(row.getCell(4).getStringCellValue()); // 4번째 열
//                                shapeEntity.setImage(row.getCell(5).getStringCellValue()); // 5번째 열
//
//                                shapeEntities.add(shapeEntity);
//                            }
//                        }
//                    } else if (file.getName().toLowerCase().endsWith(".csv")) {
//
//                        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
//                            String line;
//                            boolean skipHeader = true;
//
//                            while ((line = br.readLine()) != null) {
//                                if (skipHeader) {
//                                    // 헤더 값은 넣지 않음
//                                    skipHeader = false;
//                                    continue;
//                                }
//
//                                String[] parts = line.split(",");
//                                if (parts.length >= 13) {
//                                    ShapeEntity shapeEntity = new ShapeEntity();
//                                    shapeEntity.setSeq(parts[0]); // 0번째 열
//                                    shapeEntity.setName(parts[1]); // 1번째 열
//                                    shapeEntity.setFrontPrint(parts[6]); // 6번째 열
//                                    shapeEntity.setBackPrint(parts[7]); // 7번째 열
//                                    shapeEntity.setShape(parts[8]); // 8번째 열
//                                    shapeEntity.setFrontColor(parts[9]); // 9번째 열
//                                    shapeEntity.setBackColor(parts[10]); // 10번째 열
//                                    shapeEntity.setFrontLine(parts[11]); // 11번째 열
//                                    shapeEntity.setBackLine(parts[12]); // 12번째 열
//                                    shapeEntity.setForm(parts[4]); // 4번째 열
//                                    shapeEntity.setImage(parts[5]); // 5번째 열
//
//                                    shapeEntities.add(shapeEntity);
//                                }
//                            }
//                        }
//                    }
//                } catch (Exception e) {
//                    // 파일 읽기 오류 처리
//                    e.printStackTrace();
//                }
//            }
//
//            shapeRepository.saveAll(shapeEntities);
//
//            return true;
//        }
//
//        return false;
//    }


    
}