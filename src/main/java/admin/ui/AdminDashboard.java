package admin.ui;

import admin.network.AdminNetworkClient;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class AdminDashboard {
    private final BorderPane mainLayout;
    private final AdminNetworkClient networkClient;
    private final Runnable onLogout;
    private final AdminRoomAddPanel roomAddPanel;
    private final AdminRoomListPanel roomListPanel;
    private final AdminReservationsPanel reservationsPanel;
    private final AdminReportsPanel reportsPanel;
    private final AdminClientsPanel clientsPanel;

    public AdminDashboard(AdminNetworkClient networkClient) {
        this(networkClient, () -> {
        });
    }

    public AdminDashboard(AdminNetworkClient networkClient, Runnable onLogout) {
        this.networkClient = networkClient;
        this.onLogout = onLogout;

        // Start listening for real-time room updates
        networkClient.startListening();

        this.mainLayout = new BorderPane();
        this.mainLayout.getStyleClass().add("client-dashboard");

        this.roomAddPanel = new AdminRoomAddPanel(networkClient);
        this.roomListPanel = new AdminRoomListPanel(networkClient);
        this.reservationsPanel = new AdminReservationsPanel(networkClient);
        this.reportsPanel = new AdminReportsPanel(networkClient);
        this.clientsPanel = new AdminClientsPanel(networkClient);

        // Refresh clients/reservations when server notifies of changes
        networkClient.setClientEventListener((action, username) -> {
            if ("ADDED".equalsIgnoreCase(action)) {
                Platform.runLater(() -> clientsPanel.refresh());
            }
        });
        networkClient.setReservationEventListener((action, username, roomId) -> {
            // any reservation change should refresh admin reservations view
            Platform.runLater(() -> reservationsPanel.refresh());
        });

        showWelcomeView();

        VBox sidebarMenu = createSidebarMenu();
        mainLayout.setLeft(sidebarMenu);
    }

    private VBox createSidebarMenu() {
        VBox sidebar = new VBox();
        sidebar.setPrefWidth(250);
        sidebar.setMaxHeight(Double.MAX_VALUE);
        sidebar.getStyleClass().add("client-sidebar");

        VBox roomsBox = new VBox(5);
        Button menuAddRoom = createMenuButton("Dodaj pokój");
        Button menuListRooms = createMenuButton("Lista pokoi");

        menuAddRoom.setOnAction(e -> setContent(roomAddPanel.getRoot()));
        menuListRooms.setOnAction(e -> setContent(roomListPanel.getRoot()));

        roomsBox.getChildren().addAll(menuAddRoom, menuListRooms);
        TitledPane roomsSection = new TitledPane("Zarządzanie pokojami", roomsBox);
        roomsSection.setExpanded(true);

        VBox reservationsBox = new VBox(5);
        Button menuListReservations = createMenuButton("Rezerwacje gości");
        Button menuClients = createMenuButton("Baza klientów");

        menuListReservations.setOnAction(e -> setContent(reservationsPanel.getRoot()));
        menuClients.setOnAction(e -> setContent(clientsPanel.getRoot()));

        reservationsBox.getChildren().addAll(menuListReservations, menuClients);
        TitledPane reservationsSection = new TitledPane("Rezerwacje i Klienci", reservationsBox);
        reservationsSection.setExpanded(false);

        VBox analyticsBox = new VBox(5);
        Button menuReport = createMenuButton("Generuj raport");

        menuReport.setOnAction(e -> setContent(reportsPanel.getRoot()));

        analyticsBox.getChildren().addAll(menuReport);
        TitledPane analyticsSection = new TitledPane("Rapport i Statystyki", analyticsBox);
        analyticsSection.setExpanded(false);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button logoutButton = createMenuButton("Wyloguj");
        logoutButton.getStyleClass().remove("sidebar-nav-button");
        logoutButton.getStyleClass().add("sidebar-logout-button");
        logoutButton.setOnAction(e -> {
            networkClient.stopListening();
            onLogout.run();
        });
        VBox.setMargin(logoutButton, new Insets(0, 18, 24, 18));

        sidebar.getChildren().addAll(roomsSection, reservationsSection, analyticsSection, spacer, logoutButton);

        return sidebar;
    }

    private Button createMenuButton(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.getStyleClass().add("sidebar-nav-button");
        return btn;
    }

    private void setContent(Node newView) {
        mainLayout.setCenter(newView);
    }

    private void showWelcomeView() {
        VBox welcomeBox = new VBox(20);
        welcomeBox.setAlignment(Pos.CENTER);

        Label welcomeLabel = new Label("Witaj w Panelu Administratora");
        welcomeLabel.getStyleClass().add("header-label");

        Label subLabel = new Label("Wybierz opcję z menu po lewej stronie, aby rozpocząć zarządzanie.");
        subLabel.getStyleClass().add("subtitle-label");

        welcomeBox.getChildren().addAll(welcomeLabel, subLabel);

        setContent(welcomeBox);
    }

    private VBox createPlaceholder(String text) {
        VBox view = new VBox(20);
        view.setAlignment(Pos.CENTER);
        Label lbl = new Label(text);
        lbl.getStyleClass().add("subtitle-label");
        view.getChildren().add(lbl);
        return view;
    }

    public BorderPane getView() {
        return mainLayout;
    }
}