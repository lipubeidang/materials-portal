package cn.materials.portal;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/resources")
public class ResourceController {

  private final ResourceService resources;

  ResourceController(ResourceService resources) {
    this.resources = resources;
  }

  @GetMapping("/directories")
  Map<String, List<ResourceService.Directory>> directories() {
    return Map.of("items", resources.directories());
  }

  @GetMapping("/statistics")
  Map<String, List<ResourceService.Statistic>> statistics() {
    return Map.of("items", resources.statistics());
  }
}
