MAX_SPEED = 100
ONE_SECOND = 1
skip = false
ov = mp.create_osd_overlay("ass-events")
ov.data = "Seeking..."
-- Max noise (dB) and min silence duration (s) to trigger
opts = { quietness = -30, duration = 0.5 }


function setOptions()
    local options = require 'mp.options'
    options.read_options(opts)
end

function setTime(time)
    mp.set_property_number('time-pos', time)
end

function getTime()
    return mp.get_property_native('time-pos')
end

function setSpeed(speed)
    mp.set_property('speed', speed)
end

function getSpeed()
    return mp.get_property('speed')
end

function setPause(state)
    mp.set_property_bool('pause', state)
end

function setMute(state)
    mp.set_property_bool('mute', state)
end

function initAudioFilter()
    local af_table = mp.get_property_native('af')
    af_table[#af_table + 1] = {
        enabled = false,
        label   = 'silencedetect',
        name    = 'lavfi',
        params  = { graph = 'silencedetect=noise=' .. opts.quietness .. 'dB:d=' .. opts.duration }
    }
    mp.set_property_native('af', af_table)
end

function initVideoFilter()
    local vf_table = mp.get_property_native('vf')
    vf_table[#vf_table + 1] = {
        enabled = false,
        label   = 'blackout',
        name    = 'lavfi',
        params  = { graph = '' }
    }
    mp.set_property_native('vf', vf_table)
end

function setAudioFilter(state)
    local af_table = mp.get_property_native('af')
    if #af_table > 0 then
        for i = #af_table, 1, -1 do
            if af_table[i].label == 'silencedetect' then
                af_table[i].enabled = state
                mp.set_property_native('af', af_table)
                break
            end
        end
    end
end

function dim(state)
    local dim = { width = 0, height = 0 }
    if state == true then
        dim.width = mp.get_property_native('width')
        dim.height = mp.get_property_native('height')
    end
    return dim.width .. 'x' .. dim.height
end

function setVideoFilter(state)
    local vf_table = mp.get_property_native('vf')
    if #vf_table > 0 then
        for i = #vf_table, 1, -1 do
            if vf_table[i].label == 'blackout' then
                vf_table[i].enabled = state
                vf_table[i].params  = { graph = 'nullsink,color=c=black:s=' .. dim(state) }
                mp.set_property_native('vf', vf_table)
                break
            end
        end
    end
end

function silenceTrigger(name, value)
    if value == '{}' or value == nil then
        return
    end

    local skipTime = tonumber(string.match(value, '%d+%.?%d+'))
    local currTime = getTime()

    if skipTime == nil or skipTime < currTime + ONE_SECOND then
        return
    end

    stopSkip()
    setTime(skipTime)
    skip = false
end

function setAudioTrigger(state)
    if state == true then
        mp.observe_property('af-metadata/silencedetect', 'string', silenceTrigger)
    else
        mp.unobserve_property(silenceTrigger)   
    end
end

function startSkip()
    ov:update()
    startTime = getTime()
	startSpeed = getSpeed()
    -- This audio filter detects moments of silence
    setAudioFilter(true)
    -- This video filter makes fast-forward faster
    setVideoFilter(true)
    setAudioTrigger(true)
    setPause(false)
    setMute(true)
    setSpeed(MAX_SPEED)
end

function stopSkip()
    ov:remove()
    setAudioFilter(false)
    setVideoFilter(false)
    setAudioTrigger(false)
    setMute(false)
    setSpeed(startSpeed)
end

function keypress()
    skip = not skip
    if skip then
        startSkip()
    else
        stopSkip()
        setTime(startTime)
    end
end

setOptions(opts)
initAudioFilter()
initVideoFilter()

mp.add_key_binding(nil, 'skip-key', keypress)
