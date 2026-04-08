#!/system/bin/sh
#########################################################################################
# InputBlocker - Installation Helper
# This script handles config creation during installation
#########################################################################################

MODDIR=${0%/*}
CONFIG_DIR="$MODDIR/../config"
CONFIG_FILE="$CONFIG_DIR/blocked_regions.conf"

# Create config directory if needed
mkdir -p "$CONFIG_DIR"

# Create default config if it doesn't exist
if [ ! -f "$CONFIG_FILE" ]; then
    cat > "$CONFIG_FILE" << 'EOF'
# InputBlocker Configuration
# Format: x1,y1,x2,y2 (top-left to bottom-right coordinates)
# Lines starting with # are comments
# enabled=1 (1=enable blocking, 0=disable)
# force_safe_mode=1 (1=enable crash protection)

enabled=1
force_safe_mode=0

# Add blocked regions below (one per line):
# Example: 0,0,100,200 blocks top-left 100x200 area
# Example: 980,1720,1080,1920 blocks bottom-right corner

EOF
    chmod 644 "$CONFIG_FILE"
fi
