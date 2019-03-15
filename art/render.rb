#!/bin/env ruby

require 'xml'

resolutions = {
	'mdpi' => 1,
	'hdpi' => 1.5,
	'xhdpi' => 2,
	'xxhdpi' => 3,
	'xxxhdpi' => 4,
	}

images = {
	'main_logo.svg' => ['conversations/main_logo', 200],
    'quicksy_main_logo.svg' => ['quicksy/main_logo', 200],
    'splash_logo.svg' => ['conversations/splash_logo', 144],
    'quicksy_splash_logo.svg' => ['quicksy/splash_logo', 144],
    'ic_search_black.svg' => ['ic_search_background_black', 144],
    'ic_search_white.svg' => ['ic_search_background_white', 144],
    'ic_no_results_white.svg' => ['ic_no_results_background_white', 144],
    'ic_no_results_black.svg' => ['ic_no_results_background_black', 144],
	'play_video_white.svg' => ['play_video_white', 128],
	'play_gif_white.svg' => ['play_gif_white', 128],
    'play_video_black.svg' => ['play_video_black', 128],
    'play_gif_black.svg' => ['play_gif_black', 128],
	'conversations_mono.svg' => ['conversations/ic_notification', 24],
    'quicksy_mono.svg' => ['quicksy/ic_notification', 24],
	'ic_received_indicator.svg' => ['ic_received_indicator', 12],
	'ic_send_text_offline.svg' => ['ic_send_text_offline', 36],
	'ic_send_text_offline_white.svg' => ['ic_send_text_offline_white', 36],
	'ic_send_text_online.svg' => ['ic_send_text_online', 36],
	'ic_send_text_away.svg' => ['ic_send_text_away', 36],
	'ic_send_text_dnd.svg' => ['ic_send_text_dnd', 36],
	'ic_send_photo_online.svg' => ['ic_send_photo_online', 36],
	'ic_send_photo_offline.svg' => ['ic_send_photo_offline', 36],
	'ic_send_photo_offline_white.svg' => ['ic_send_photo_offline_white', 36],
	'ic_send_photo_away.svg' => ['ic_send_photo_away', 36],
	'ic_send_photo_dnd.svg' => ['ic_send_photo_dnd', 36],
	'ic_send_location_online.svg' => ['ic_send_location_online', 36],
	'ic_send_location_offline.svg' => ['ic_send_location_offline', 36],
	'ic_send_location_offline_white.svg' => ['ic_send_location_offline_white', 36],
	'ic_send_location_away.svg' => ['ic_send_location_away', 36],
	'ic_send_location_dnd.svg' => ['ic_send_location_dnd', 36],
	'ic_send_voice_online.svg' => ['ic_send_voice_online', 36],
	'ic_send_voice_offline.svg' => ['ic_send_voice_offline', 36],
	'ic_send_voice_offline_white.svg' => ['ic_send_voice_offline_white', 36],
	'ic_send_voice_away.svg' => ['ic_send_voice_away', 36],
	'ic_send_voice_dnd.svg' => ['ic_send_voice_dnd', 36],
	'ic_send_cancel_online.svg' => ['ic_send_cancel_online', 36],
	'ic_send_cancel_offline.svg' => ['ic_send_cancel_offline', 36],
	'ic_send_cancel_offline_white.svg' => ['ic_send_cancel_offline_white', 36],
	'ic_send_cancel_away.svg' => ['ic_send_cancel_away', 36],
	'ic_send_cancel_dnd.svg' => ['ic_send_cancel_dnd', 36],
	'ic_send_picture_online.svg' => ['ic_send_picture_online', 36],
	'ic_send_picture_offline.svg' => ['ic_send_picture_offline', 36],
	'ic_send_picture_offline_white.svg' => ['ic_send_picture_offline_white', 36],
	'ic_send_picture_away.svg' => ['ic_send_picture_away', 36],
	'ic_send_picture_dnd.svg' => ['ic_send_picture_dnd', 36],
	'ic_send_videocam_online.svg' => ['ic_send_videocam_online', 36],
	'ic_send_videocam_offline.svg' => ['ic_send_videocam_offline', 36],
	'ic_send_videocam_offline_white.svg' => ['ic_send_videocam_offline_white', 36],
	'ic_send_videocam_away.svg' => ['ic_send_videocam_away', 36],
	'ic_send_videocam_dnd.svg' => ['ic_send_videocam_dnd', 36],
	'ic_notifications_none_white80.svg' => ['ic_notifications_none_white80', 24],
	'ic_notifications_off_white80.svg' => ['ic_notifications_off_white80', 24],
	'ic_notifications_paused_white80.svg' => ['ic_notifications_paused_white80', 24],
	'ic_notifications_white80.svg' => ['ic_notifications_white80', 24],
	'ic_verified_fingerprint.svg' => ['ic_verified_fingerprint', 36],
    'qrcode-scan.svg' => ['ic_qr_code_scan_white_24dp', 24],
	'message_bubble_received.svg' => ['message_bubble_received.9', 0],
	'message_bubble_received_grey.svg' => ['message_bubble_received_grey.9', 0],
	'message_bubble_received_dark.svg' => ['message_bubble_received_dark.9', 0],
	'message_bubble_received_warning.svg' => ['message_bubble_received_warning.9', 0],
	'message_bubble_received_white.svg' => ['message_bubble_received_white.9', 0],
	'message_bubble_sent.svg' => ['message_bubble_sent.9', 0],
	'message_bubble_sent_grey.svg' => ['message_bubble_sent_grey.9', 0],
	'date_bubble_white.svg' => ['date_bubble_white.9', 0],
	'date_bubble_grey.svg' => ['date_bubble_grey.9', 0],
	'marker.svg' => ['marker', 0]
	}

# Executable paths for Mac OSX
# "/Applications/Inkscape.app/Contents/Resources/bin/inkscape"

inkscape = "inkscape"
imagemagick = "magick"

def execute_cmd(cmd)
	puts cmd
	system cmd
end

images.each do |source_filename, settings|
	svg_content = File.read(source_filename)

	svg = XML::Document.string(svg_content)
	base_width = svg.root["width"].to_i
	base_height = svg.root["height"].to_i

	guides = svg.find(".//sodipodi:guide","sodipodi:http://sodipodi.sourceforge.net/DTD/sodipodi-0.dtd")

	resolutions.each do |resolution, factor|
		output_filename, base_size = settings

		if base_size > 0
			width = factor * base_size
			height = factor * base_size
		else
			width = factor * base_width
			height = factor * base_height
		end

        output_parts = output_filename.split('/')

        if output_parts.count != 2
    		path = "../src/main/res/drawable-#{resolution}/#{output_filename}.png"
        else
            path = "../src/#{output_parts[0]}/res/drawable-#{resolution}/#{output_parts[1]}.png"
        end
		execute_cmd "#{inkscape} -f #{source_filename} -z -C -w #{width} -h #{height} -e #{path}"

		top = []
		right = []
		bottom = []
		left = []

		guides.each do |guide|
			orientation = guide["orientation"]
			x, y = guide["position"].split(",")
			x, y = x.to_i, y.to_i

			if orientation == "1,0" and y == base_height
				top.push(x * factor)
			end

			if orientation == "0,1" and x == base_width
				right.push((base_height - y) * factor)
			end

			if orientation == "1,0" and y == 0
				bottom.push(x * factor)
			end

			if orientation == "0,1" and x == 0
				left.push((base_height - y) * factor)
			end
		end

		next if top.length != 2
		next if right.length != 2
		next if bottom.length != 2
		next if left.length != 2

		execute_cmd "#{imagemagick} -background none PNG32:#{path} -gravity center -extent #{width+2}x#{height+2} PNG32:#{path}"

		draw_format = "-draw \"line %d,%d %d,%d\""
		top_line = draw_format % [top.min + 1, 0, top.max, 0]
		right_line = draw_format % [width + 1, right.min + 1, width + 1, right.max]
		bottom_line = draw_format % [bottom.min + 1, height + 1, bottom.max, height + 1]
		left_line = draw_format % [0, left.min + 1, 0, left.max]
		draws = "#{top_line} #{right_line} #{bottom_line} #{left_line}"

		execute_cmd "#{imagemagick} -background none PNG32:#{path} -fill black -stroke none #{draws} PNG32:#{path}"
	end
end
