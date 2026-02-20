package com.runeliteagent;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

@Slf4j
public class ClaudeAgentPanel extends PluginPanel
{
    private static final String CONFIG_GROUP = "claudeagent";
    private static final Color USER_BG = new Color(44, 62, 80);
    private static final Color ASSISTANT_BG = new Color(30, 39, 46);
    private static final Color TOOL_BG = new Color(39, 55, 40);
    private static final Color TEXT_COLOR = new Color(236, 240, 241);
    private static final Color MUTED_COLOR = new Color(149, 165, 166);
    private static final Color ACCENT_COLOR = new Color(52, 152, 219);
    private static final Color ERROR_COLOR = new Color(231, 76, 60);
    private static final Color SUCCESS_COLOR = new Color(46, 204, 113);
    private static final Font MSG_FONT = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font LABEL_FONT = new Font("SansSerif", Font.BOLD, 11);

    private final AgentOrchestrator orchestrator;
    private final ConfigManager configManager;

    private CardLayout cardLayout;
    private JPanel cardPanel;

    // Setup view
    private JPasswordField apiKeyField;
    private JLabel setupStatus;

    // Chat view
    private JPanel chatContainer;
    private JScrollPane chatScroll;
    private JTextArea inputArea;
    private JButton sendButton;
    private JButton clearButton;
    private JPanel currentAssistantBubble;
    private JTextArea currentAssistantText;
    private boolean isProcessing = false;

    public ClaudeAgentPanel(AgentOrchestrator orchestrator, ConfigManager configManager)
    {
        super(false);
        this.orchestrator = orchestrator;
        this.configManager = configManager;
        buildUI();
    }

    private void buildUI()
    {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        cardPanel.add(buildSetupView(), "setup");
        cardPanel.add(buildChatView(), "chat");

        add(cardPanel, BorderLayout.CENTER);

        // Show the right view based on whether key is configured
        if (hasApiKey())
        {
            cardLayout.show(cardPanel, "chat");
        }
        else
        {
            cardLayout.show(cardPanel, "setup");
        }
    }

    private boolean hasApiKey()
    {
        String key = configManager.getConfiguration(CONFIG_GROUP, "apiKey");
        return key != null && !key.trim().isEmpty();
    }

    // ========== SETUP VIEW ==========

    private JPanel buildSetupView()
    {
        JPanel setup = new JPanel(new BorderLayout());
        setup.setBackground(ColorScheme.DARK_GRAY_COLOR);
        setup.setBorder(new EmptyBorder(20, 15, 20, 15));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Title
        JLabel title = new JLabel("Claude Agent Setup");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setForeground(TEXT_COLOR);
        title.setAlignmentX(0);
        title.setBorder(new EmptyBorder(0, 0, 15, 0));
        content.add(title);

        // Description
        JTextArea desc = new JTextArea(
            "Enter your Anthropic API key to get started.\n\n"
            + "Get a key at:\nconsole.anthropic.com/settings/keys"
        );
        desc.setFont(MSG_FONT);
        desc.setForeground(MUTED_COLOR);
        desc.setBackground(ColorScheme.DARK_GRAY_COLOR);
        desc.setEditable(false);
        desc.setLineWrap(true);
        desc.setWrapStyleWord(true);
        desc.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        desc.setAlignmentX(0);
        desc.setBorder(new EmptyBorder(0, 0, 15, 0));
        content.add(desc);

        // API Key label
        JLabel keyLabel = new JLabel("API Key");
        keyLabel.setFont(LABEL_FONT);
        keyLabel.setForeground(TEXT_COLOR);
        keyLabel.setAlignmentX(0);
        keyLabel.setBorder(new EmptyBorder(0, 0, 5, 0));
        content.add(keyLabel);

        // API Key field
        apiKeyField = new JPasswordField();
        apiKeyField.setFont(MSG_FONT);
        apiKeyField.setForeground(TEXT_COLOR);
        apiKeyField.setBackground(USER_BG);
        apiKeyField.setCaretColor(TEXT_COLOR);
        apiKeyField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(52, 73, 94), 1),
            new EmptyBorder(8, 8, 8, 8)
        ));
        apiKeyField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        apiKeyField.setAlignmentX(0);
        apiKeyField.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_ENTER)
                {
                    saveApiKey();
                }
            }
        });
        content.add(apiKeyField);

        // Status label
        setupStatus = new JLabel(" ");
        setupStatus.setFont(new Font("SansSerif", Font.PLAIN, 11));
        setupStatus.setForeground(MUTED_COLOR);
        setupStatus.setAlignmentX(0);
        setupStatus.setBorder(new EmptyBorder(8, 0, 15, 0));
        content.add(setupStatus);

        // Save button
        JButton saveBtn = new JButton("Save & Start Chatting");
        saveBtn.setFont(LABEL_FONT);
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setBackground(ACCENT_COLOR);
        saveBtn.setBorder(new EmptyBorder(10, 20, 10, 20));
        saveBtn.setFocusPainted(false);
        saveBtn.setAlignmentX(0);
        saveBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        saveBtn.addActionListener(e -> saveApiKey());
        content.add(saveBtn);

        setup.add(content, BorderLayout.NORTH);
        return setup;
    }

    private void saveApiKey()
    {
        String key = new String(apiKeyField.getPassword()).trim();
        if (key.isEmpty())
        {
            setupStatus.setForeground(ERROR_COLOR);
            setupStatus.setText("Please enter an API key.");
            return;
        }
        if (!key.startsWith("sk-"))
        {
            setupStatus.setForeground(ERROR_COLOR);
            setupStatus.setText("Key should start with 'sk-'");
            return;
        }

        log.info("Saving API key. Length: {}, prefix: {}...", key.length(), key.substring(0, Math.min(10, key.length())));

        // Write directly to ConfigManager
        configManager.setConfiguration(CONFIG_GROUP, "apiKey", key);

        // Verify it was saved
        String saved = configManager.getConfiguration(CONFIG_GROUP, "apiKey");
        if (saved != null && saved.equals(key))
        {
            log.info("API key saved and verified successfully.");
            setupStatus.setForeground(SUCCESS_COLOR);
            setupStatus.setText("✓ Key saved!");
            // Switch to chat view
            SwingUtilities.invokeLater(() -> cardLayout.show(cardPanel, "chat"));
        }
        else
        {
            log.error("API key verification failed. Saved: '{}', Expected length: {}", saved, key.length());
            setupStatus.setForeground(ERROR_COLOR);
            setupStatus.setText("Failed to save key. Try again.");
        }
    }

    // ========== CHAT VIEW ==========

    private JPanel buildChatView()
    {
        JPanel chat = new JPanel(new BorderLayout());
        chat.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(new EmptyBorder(8, 10, 8, 10));

        JLabel chatTitle = new JLabel("Claude Agent");
        chatTitle.setForeground(TEXT_COLOR);
        chatTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        header.add(chatTitle, BorderLayout.WEST);

        JPanel headerButtons = new JPanel();
        headerButtons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerButtons.setLayout(new BoxLayout(headerButtons, BoxLayout.X_AXIS));

        JButton keyBtn = new JButton("Key");
        keyBtn.setFont(LABEL_FONT);
        keyBtn.setForeground(MUTED_COLOR);
        keyBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        keyBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(MUTED_COLOR, 1),
            new EmptyBorder(4, 6, 4, 6)
        ));
        keyBtn.setFocusPainted(false);
        keyBtn.addActionListener(e -> cardLayout.show(cardPanel, "setup"));
        headerButtons.add(keyBtn);

        headerButtons.add(javax.swing.Box.createHorizontalStrut(5));

        clearButton = new JButton("Clear");
        clearButton.setFont(LABEL_FONT);
        clearButton.setForeground(MUTED_COLOR);
        clearButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        clearButton.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(MUTED_COLOR, 1),
            new EmptyBorder(4, 6, 4, 6)
        ));
        clearButton.setFocusPainted(false);
        clearButton.addActionListener(e -> clearChat());
        headerButtons.add(clearButton);

        header.add(headerButtons, BorderLayout.EAST);
        chat.add(header, BorderLayout.NORTH);

        // Chat area
        chatContainer = new JPanel();
        chatContainer.setLayout(new BoxLayout(chatContainer, BoxLayout.Y_AXIS));
        chatContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        chatContainer.setBorder(new EmptyBorder(5, 5, 5, 5));

        chatScroll = new JScrollPane(chatContainer);
        chatScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        chatScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        chatScroll.setBorder(null);
        chatScroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        chat.add(chatScroll, BorderLayout.CENTER);

        // Input area
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        inputPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        inputArea = new JTextArea(3, 20);
        inputArea.setFont(MSG_FONT);
        inputArea.setForeground(TEXT_COLOR);
        inputArea.setBackground(USER_BG);
        inputArea.setCaretColor(TEXT_COLOR);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(new EmptyBorder(6, 8, 6, 8));
        inputArea.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown())
                {
                    e.consume();
                    sendMessage();
                }
            }
        });

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createLineBorder(new Color(52, 73, 94), 1));
        inputScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        inputPanel.add(inputScroll, BorderLayout.CENTER);

        sendButton = new JButton("Send");
        sendButton.setFont(LABEL_FONT);
        sendButton.setForeground(Color.WHITE);
        sendButton.setBackground(ACCENT_COLOR);
        sendButton.setBorder(new EmptyBorder(8, 14, 8, 14));
        sendButton.setFocusPainted(false);
        sendButton.setPreferredSize(new Dimension(60, 0));
        sendButton.addActionListener(e -> sendMessage());
        inputPanel.add(sendButton, BorderLayout.EAST);

        chat.add(inputPanel, BorderLayout.SOUTH);

        // Welcome message
        addSystemMessage("Welcome! I'm your Claude-powered OSRS assistant.\n\n"
            + "I can help you with:\n"
            + "• Toggle RuneLite plugins on/off\n"
            + "• Change plugin settings\n"
            + "• Look up OSRS Wiki info\n"
            + "• Answer game questions\n\n"
            + "Type a message to get started!");

        return chat;
    }

    private void sendMessage()
    {
        String text = inputArea.getText().trim();
        if (text.isEmpty() || isProcessing)
        {
            return;
        }

        if (!hasApiKey())
        {
            addSystemMessage("⚠️ Please set your API key first (click 'Key' button).");
            return;
        }

        isProcessing = true;
        sendButton.setEnabled(false);
        sendButton.setText("...");
        inputArea.setText("");

        addUserMessage(text);
        currentAssistantBubble = null;
        currentAssistantText = null;

        orchestrator.sendMessage(text,
            chunk -> SwingUtilities.invokeLater(() -> {
                if (chunk.startsWith("\n🔧"))
                {
                    addToolMessage(chunk.trim());
                }
                else
                {
                    appendToAssistantMessage(chunk);
                }
            }),
            complete -> SwingUtilities.invokeLater(() -> {
                isProcessing = false;
                sendButton.setEnabled(true);
                sendButton.setText("Send");
                addDoneIndicator();
                scrollToBottom();
            }),
            error -> SwingUtilities.invokeLater(() -> {
                addSystemMessage("❌ " + error);
                isProcessing = false;
                sendButton.setEnabled(true);
                sendButton.setText("Send");
            })
        );
    }

    private void addUserMessage(String text)
    {
        JPanel bubble = createBubble("You", text, USER_BG, ACCENT_COLOR);
        chatContainer.add(bubble);
        chatContainer.revalidate();
        scrollToBottom();
    }

    private void appendToAssistantMessage(String text)
    {
        if (currentAssistantBubble == null)
        {
            currentAssistantText = new JTextArea();
            currentAssistantText.setFont(MSG_FONT);
            currentAssistantText.setForeground(TEXT_COLOR);
            currentAssistantText.setBackground(ASSISTANT_BG);
            currentAssistantText.setEditable(false);
            currentAssistantText.setLineWrap(true);
            currentAssistantText.setWrapStyleWord(true);
            currentAssistantText.setBorder(null);

            currentAssistantBubble = new JPanel(new BorderLayout());
            currentAssistantBubble.setBackground(ASSISTANT_BG);
            currentAssistantBubble.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(3, 0, 3, 0),
                new EmptyBorder(8, 10, 8, 10)
            ));
            currentAssistantBubble.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

            JLabel label = new JLabel("Claude");
            label.setFont(LABEL_FONT);
            label.setForeground(SUCCESS_COLOR);
            label.setBorder(new EmptyBorder(0, 0, 4, 0));
            currentAssistantBubble.add(label, BorderLayout.NORTH);
            currentAssistantBubble.add(currentAssistantText, BorderLayout.CENTER);

            chatContainer.add(currentAssistantBubble);
            chatContainer.revalidate();
        }

        currentAssistantText.append(text);
        chatContainer.revalidate();
        scrollToBottom();
    }

    private void addDoneIndicator()
    {
        JPanel indicator = new JPanel(new BorderLayout());
        indicator.setBackground(ColorScheme.DARK_GRAY_COLOR);
        indicator.setBorder(new EmptyBorder(2, 10, 2, 10));
        indicator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel doneLabel = new JLabel("✅ Done");
        doneLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
        doneLabel.setForeground(SUCCESS_COLOR);
        indicator.add(doneLabel, BorderLayout.WEST);

        chatContainer.add(indicator);
        chatContainer.revalidate();
        currentAssistantBubble = null;
        currentAssistantText = null;
    }

    private void addToolMessage(String text)
    {
        JPanel bubble = createBubble("Tool", text, TOOL_BG, new Color(241, 196, 15));
        chatContainer.add(bubble);
        chatContainer.revalidate();
        scrollToBottom();
    }

    private void addSystemMessage(String text)
    {
        JPanel bubble = createBubble("System", text, ColorScheme.DARKER_GRAY_COLOR, MUTED_COLOR);
        chatContainer.add(bubble);
        chatContainer.revalidate();
        scrollToBottom();
    }

    private JPanel createBubble(String sender, String text, Color bg, Color labelColor)
    {
        JPanel bubble = new JPanel(new BorderLayout());
        bubble.setBackground(bg);
        bubble.setBorder(BorderFactory.createCompoundBorder(
            new EmptyBorder(3, 0, 3, 0),
            new EmptyBorder(8, 10, 8, 10)
        ));
        bubble.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel label = new JLabel(sender);
        label.setFont(LABEL_FONT);
        label.setForeground(labelColor);
        label.setBorder(new EmptyBorder(0, 0, 4, 0));
        bubble.add(label, BorderLayout.NORTH);

        JTextArea content = new JTextArea(text);
        content.setFont(MSG_FONT);
        content.setForeground(TEXT_COLOR);
        content.setBackground(bg);
        content.setEditable(false);
        content.setLineWrap(true);
        content.setWrapStyleWord(true);
        content.setBorder(null);
        bubble.add(content, BorderLayout.CENTER);

        return bubble;
    }

    private void scrollToBottom()
    {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = chatScroll.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private void clearChat()
    {
        chatContainer.removeAll();
        chatContainer.revalidate();
        chatContainer.repaint();
        currentAssistantBubble = null;
        currentAssistantText = null;
        orchestrator.clearHistory();
        addSystemMessage("Chat cleared. Ready for a new conversation.");
    }
}
