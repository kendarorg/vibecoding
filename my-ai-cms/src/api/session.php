<?php
require_once "../Settings.php";
require_once "../Sessions.php";

// Initialize session
$session = new Session();

// Set headers for JSON response
header('Content-Type: application/json');

// Handle the request based on action parameter
$action = isset($_GET['action']) ? $_GET['action'] : '';

switch ($action) {
    case 'getExpanded':
        // Return currently expanded nodes
        $opened = $session->get('opened', []);
        echo json_encode([
            'success' => true,
            'opened' => $opened
        ]);
        break;

    case 'addExpanded':
        // Add a node to expanded list
        $requestData = json_decode(file_get_contents('php://input'), true);
        $nodeId = isset($requestData['nodeId']) ? $requestData['nodeId'] : null;

        if ($nodeId) {
            $opened = $session->get('opened', []);

            // Add node if not already in array
            if (!in_array($nodeId, $opened)) {
                $opened[] = $nodeId;
                $session->set('opened', $opened);
            }

            echo json_encode([
                'success' => true
            ]);
        } else {
            echo json_encode([
                'success' => false,
                'message' => 'Node ID is required'
            ]);
        }
        break;

    case 'removeExpanded':
        // Remove a node from expanded list
        $requestData = json_decode(file_get_contents('php://input'), true);
        $nodeId = isset($requestData['nodeId']) ? $requestData['nodeId'] : null;

        if ($nodeId) {
            $opened = $session->get('opened', []);

            // Remove this node
            $opened = array_values(array_filter($opened, function($id) use ($nodeId) {
                return $id !== $nodeId;
            }));

            // Also remove any child nodes (nodes that were under this one)
            // This would require knowing the hierarchy - for now we'll skip this
            // In a real implementation, you'd either store the full path or
            // fetch children from storage to know which nodes to remove

            $session->set('opened', $opened);

            echo json_encode([
                'success' => true
            ]);
        } else {
            echo json_encode([
                'success' => false,
                'message' => 'Node ID is required'
            ]);
        }
        break;

    default:
        echo json_encode([
            'success' => false,
            'message' => 'Invalid action'
        ]);
}