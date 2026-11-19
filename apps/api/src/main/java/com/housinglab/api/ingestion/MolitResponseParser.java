package com.housinglab.api.ingestion;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/** 실거래가 API XML 응답을 항목별 필드 맵으로 바꾼다. */
public final class MolitResponseParser {
  private MolitResponseParser() {}

  public record Page(int totalCount, List<Map<String, String>> items) {}

  public static Page parse(String xml) {
    Document doc = read(xml);
    Element root = doc.getDocumentElement();
    if ("OpenAPI_ServiceResponse".equals(root.getTagName())) {
      // 게이트웨이 오류(미등록 키, 트래픽 초과 등)
      throw new MolitApiException(text(root, "returnReasonCode"), text(root, "returnAuthMsg") + " (" + text(root, "errMsg") + ")");
    }
    String code = text(root, "resultCode");
    if (!"000".equals(code) && !"00".equals(code)) throw new MolitApiException(code, text(root, "resultMsg"));

    var items = new ArrayList<Map<String, String>>();
    var nodes = root.getElementsByTagName("item");
    for (int i = 0; i < nodes.getLength(); i++) {
      var fields = new LinkedHashMap<String, String>();
      for (Node child = nodes.item(i).getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child.getNodeType() == Node.ELEMENT_NODE) fields.put(child.getNodeName(), child.getTextContent().trim());
      }
      items.add(fields);
    }
    String total = text(root, "totalCount");
    return new Page(total.isEmpty() ? items.size() : Integer.parseInt(total), items);
  }

  private static Document read(String xml) {
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    } catch (Exception e) {
      throw new MolitApiException("PARSE", "XML 응답을 해석할 수 없습니다: " + e.getMessage());
    }
  }

  private static String text(Element root, String tag) {
    var nodes = root.getElementsByTagName(tag);
    return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
  }
}
