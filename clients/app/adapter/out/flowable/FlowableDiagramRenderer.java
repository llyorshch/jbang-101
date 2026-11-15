package app.adapter.out.flowable;

import app.application.port.out.CaseDiagramRenderer;
import app.domain.cases.CaseType;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.flowable.cmmn.api.CmmnHistoryService;
import org.flowable.cmmn.api.CmmnRepositoryService;
import org.flowable.cmmn.api.history.HistoricPlanItemInstance;
import org.flowable.cmmn.api.runtime.PlanItemDefinitionType;
import org.flowable.cmmn.api.runtime.PlanItemInstanceState;
import org.flowable.cmmn.image.impl.DefaultCaseDiagramGenerator;
import org.flowable.cmmn.model.CmmnModel;
import org.flowable.cmmn.model.GraphicInfo;
import org.flowable.cmmn.model.Milestone;
import org.flowable.cmmn.model.UserEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Renders a case as PNG: the Flowable diagram of the case's own definition version, with Java2D
 * overlays showing the state of each plan item. Works for running and ended cases (reads history).
 */
@Component
class FlowableDiagramRenderer implements CaseDiagramRenderer {

  private static final Logger log = LoggerFactory.getLogger(FlowableDiagramRenderer.class);

  /** Flowable crops the canvas to the diagram's top-left corner minus this margin. */
  private static final int GENERATOR_MARGIN = 5;

  private static final int LEGEND_HEIGHT = 34;
  private static final Color ACTIVE = new Color(34, 139, 34);
  private static final Color DONE = new Color(120, 120, 120);
  private static final Color DONE_FILL = new Color(160, 160, 160, 70);
  private static final Color AVAILABLE = new Color(230, 126, 34);
  private static final Color REACHED_FILL = new Color(46, 160, 67, 110);
  private static final Stroke ACTIVE_STROKE = new BasicStroke(4f);
  private static final Stroke DONE_STROKE = new BasicStroke(2f);
  private static final Stroke AVAILABLE_STROKE =
      new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {6f, 4f}, 0f);

  private static final List<String> OVERLAID_TYPES =
      List.of(
          PlanItemDefinitionType.STAGE,
          PlanItemDefinitionType.HUMAN_TASK,
          PlanItemDefinitionType.SERVICE_TASK,
          PlanItemDefinitionType.MILESTONE,
          PlanItemDefinitionType.USER_EVENT_LISTENER);

  private final CmmnHistoryService history;
  private final CmmnRepositoryService repository;

  FlowableDiagramRenderer(CmmnHistoryService history, CmmnRepositoryService repository) {
    this.history = history;
    this.repository = repository;
  }

  @Override
  public Optional<byte[]> renderPng(CaseType type, String caseId) {
    var instance =
        history
            .createHistoricCaseInstanceQuery()
            .caseInstanceId(caseId)
            .caseDefinitionKey(PlanItems.definitionKey(type))
            .singleResult();
    if (instance == null) {
      return Optional.empty();
    }
    // The case's own definition version; versions deployed before the CMMNDI layout have none.
    var model = repository.getCmmnModel(instance.getCaseDefinitionId());
    if (model == null || model.getLocationMap().isEmpty()) {
      return Optional.empty();
    }
    var base = new DefaultCaseDiagramGenerator().generateImage(model, "png", 1.0);
    var image = withLegendStrip(base);
    var graphics = image.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.translate(-cropOffset(model, true), -cropOffset(model, false));
      currentStates(caseId).forEach((elementId, item) -> overlay(graphics, model, elementId, item));
      drawMissingLabels(graphics, model);
      graphics.translate(cropOffset(model, true), cropOffset(model, false));
      drawLegend(graphics, base.getHeight());
    } finally {
      graphics.dispose();
    }
    log.debug("Rendered diagram of {} case {}", type, caseId);
    return Optional.of(encode(image));
  }

  /** Latest relevant instance per plan item (keyed by plan item id, the CMMNShape reference). */
  private Map<String, ItemState> currentStates(String caseId) {
    return history
        .createHistoricPlanItemInstanceQuery()
        .planItemInstanceCaseInstanceId(caseId)
        .planItemInstanceDefinitionTypes(OVERLAID_TYPES)
        .list()
        .stream()
        .collect(Collectors.groupingBy(HistoricPlanItemInstance::getElementId))
        .entrySet()
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> ItemState.of(entry.getValue())));
  }

  private static void overlay(Graphics2D g, CmmnModel model, String elementId, ItemState item) {
    var info = model.getGraphicInfo(elementId);
    if (info == null) {
      return;
    }
    var isListener = PlanItemDefinitionType.USER_EVENT_LISTENER.equals(item.type());
    var isMilestone = PlanItemDefinitionType.MILESTONE.equals(item.type());
    Shape outline = isListener ? circle(info) : box(info, isMilestone ? 24 : 10);
    switch (item.state()) {
      case PlanItemInstanceState.ACTIVE -> {
        if (!isMilestone && !isListener) {
          stroke(g, outline, ACTIVE, ACTIVE_STROKE);
        }
      }
      case PlanItemInstanceState.AVAILABLE -> {
        if (isListener) {
          stroke(g, outline, AVAILABLE, AVAILABLE_STROKE);
        }
      }
      case PlanItemInstanceState.COMPLETED, PlanItemInstanceState.TERMINATED -> {
        if (isMilestone) {
          if (PlanItemInstanceState.COMPLETED.equals(item.state())) {
            fill(g, outline, REACHED_FILL);
            stroke(g, outline, ACTIVE, DONE_STROKE);
          }
        } else {
          fill(g, outline, DONE_FILL);
          stroke(g, outline, DONE, DONE_STROKE);
        }
      }
      default -> {
        // Not started (or waiting for a repetition): the base diagram already shows it.
      }
    }
  }

  /** The generator draws no names for milestones and event listeners; add them. */
  private static void drawMissingLabels(Graphics2D g, CmmnModel model) {
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
    g.setColor(Color.BLACK);
    var metrics = g.getFontMetrics();
    model.getLocationMap().forEach(
        (elementId, info) -> {
          var planItem = model.findPlanItem(elementId);
          if (planItem == null || planItem.getName() == null) {
            return;
          }
          var definition = planItem.getPlanItemDefinition();
          var textWidth = metrics.stringWidth(planItem.getName());
          var centreX = (int) (info.getX() + info.getWidth() / 2) - textWidth / 2;
          if (definition instanceof Milestone) {
            var baseline = (int) (info.getY() + info.getHeight() / 2) + metrics.getAscent() / 2;
            g.drawString(planItem.getName(), centreX, baseline);
          } else if (definition instanceof UserEventListener) {
            var baseline = (int) (info.getY() + info.getHeight()) + metrics.getAscent() + 8;
            g.drawString(planItem.getName(), centreX, baseline);
          }
        });
  }

  private static Shape box(GraphicInfo info, double arc) {
    return new RoundRectangle2D.Double(
        info.getX() - 2, info.getY() - 2, info.getWidth() + 4, info.getHeight() + 4, arc, arc);
  }

  private static Shape circle(GraphicInfo info) {
    return new Ellipse2D.Double(
        info.getX() - 4, info.getY() - 4, info.getWidth() + 8, info.getHeight() + 8);
  }

  private static void stroke(Graphics2D g, Shape shape, Color color, Stroke stroke) {
    g.setColor(color);
    g.setStroke(stroke);
    g.draw(shape);
  }

  private static void fill(Graphics2D g, Shape shape, Color color) {
    g.setColor(color);
    g.fill(shape);
  }

  /**
   * Replicates how the generator crops its canvas: it starts the image at {@code min - 5}, where
   * min is the smallest coordinate of any shape or connector waypoint, floored at 5.
   */
  private static int cropOffset(CmmnModel model, boolean horizontal) {
    var shapes = model.getLocationMap().values().stream();
    var waypoints = model.getFlowLocationMap().values().stream().flatMap(Collection::stream);
    var min =
        Stream.concat(shapes, waypoints)
            .mapToDouble(info -> horizontal ? info.getX() : info.getY())
            .min()
            .orElse(0);
    return Math.max((int) min, GENERATOR_MARGIN) - GENERATOR_MARGIN;
  }

  private static BufferedImage withLegendStrip(BufferedImage base) {
    var image =
        new BufferedImage(
            base.getWidth(), base.getHeight() + LEGEND_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    var g = image.createGraphics();
    try {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, image.getWidth(), image.getHeight());
      g.drawImage(base, 0, 0, null);
    } finally {
      g.dispose();
    }
    return image;
  }

  private static void drawLegend(Graphics2D g, int top) {
    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    var y = top + 9;
    var x = 12;
    x = legendEntry(g, x, y, "Active", null, ACTIVE, ACTIVE_STROKE, false);
    x = legendEntry(g, x, y, "Completed / ended", DONE_FILL, DONE, DONE_STROKE, false);
    x = legendEntry(g, x, y, "Available event", null, AVAILABLE, AVAILABLE_STROKE, true);
    legendEntry(g, x, y, "Milestone reached", REACHED_FILL, ACTIVE, DONE_STROKE, false);
  }

  private static int legendEntry(
      Graphics2D g, int x, int y, String label, Color fill, Color line, Stroke stroke, boolean round) {
    Shape swatch =
        round
            ? new Ellipse2D.Double(x, y, 16, 16)
            : new RoundRectangle2D.Double(x, y, 26, 16, 6, 6);
    if (fill != null) {
      fill(g, swatch, fill);
    }
    stroke(g, swatch, line, stroke);
    var textX = x + (round ? 24 : 34);
    g.setColor(Color.DARK_GRAY);
    g.drawString(label, textX, y + 13);
    return textX + g.getFontMetrics().stringWidth(label) + 22;
  }

  private static byte[] encode(BufferedImage image) {
    try (var out = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** State to display for one plan item, chosen among its (possibly repeated) instances. */
  private record ItemState(String type, String state) {

    /**
     * An available user event listener is shown as available. Otherwise the most recently
     * progressed instance wins (so a repeatable stage shows its latest round, not the instance
     * waiting for the next repetition); items that never progressed fall back to the newest one.
     */
    static ItemState of(List<HistoricPlanItemInstance> instances) {
      var first = instances.getFirst();
      if (PlanItemDefinitionType.USER_EVENT_LISTENER.equals(first.getPlanItemDefinitionType())
          && instances.stream()
              .anyMatch(i -> PlanItemInstanceState.AVAILABLE.equals(i.getState()))) {
        return new ItemState(first.getPlanItemDefinitionType(), PlanItemInstanceState.AVAILABLE);
      }
      var latest =
          instances.stream()
              .filter(i -> progressedAt(i).isPresent())
              .max(Comparator.comparing(i -> progressedAt(i).orElseThrow()))
              .or(
                  () ->
                      instances.stream()
                          .max(Comparator.comparing(HistoricPlanItemInstance::getCreateTime)))
              .orElse(first);
      return new ItemState(latest.getPlanItemDefinitionType(), latest.getState());
    }

    private static Optional<Date> progressedAt(HistoricPlanItemInstance instance) {
      return Stream.of(
              instance.getLastStartedTime(),
              instance.getOccurredTime(),
              instance.getCompletedTime())
          .filter(Objects::nonNull)
          .findFirst();
    }
  }
}
