<?php
require_once("Settings.php");
global  $session;
$session->checkLoggedIn();
?>
<div class="export-section form-row">
    <h3>Export Data</h3>
    <form action="api/exporter.php" method="get" target="_blank">
        <div class="">
            <label for="type">Content Type:</label>
            <select id="type" name="type">
                <option value="all">All Files with html</option>
                <option value="md">Markdown Only</option>
                <option value="images">Images Only</option>
                <option value="browsable">Browsable Html</option>
                <option value="backup">Backup</option>
            </select>
        </div>
        <br>
       <button type="submit" class="btn">Export</button>
    </form>
</div>